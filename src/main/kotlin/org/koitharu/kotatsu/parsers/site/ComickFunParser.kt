package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArraySet
import androidx.collection.SparseArrayCompat
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * https://api.comick.fun/docs/static/index.html
 */

private const val PAGE_SIZE = 20
private const val CHAPTERS_LIMIT = 99999

@MangaSourceParser("COMICK_FUN", "ComicK")
internal class ComickFunParser(context: MangaLoaderContext) : MangaParser(context, MangaSource.COMICK_FUN) {

	override val configKeyDomain = ConfigKey.Domain("comick.app", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.RATING,
	)

	@Volatile
	private var cachedTags: SparseArrayCompat<MangaTag>? = null

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val domain = domain
		val url = buildString {
			append("https://api.")
			append(domain)
			append("/v1.0/search?tachiyomi=true")
			if (!query.isNullOrEmpty()) {
				if (offset > 0) {
					return emptyList()
				}
				append("&q=")
				append(query.urlEncoded())
			} else {
				append("&limit=")
				append(PAGE_SIZE)
				append("&page=")
				append((offset / PAGE_SIZE) + 1)
				if (!tags.isNullOrEmpty()) {
					append("&genres=")
					appendAll(tags, "&genres=", MangaTag::key)
				}
				append("&sort=") // view, uploaded, rating, follow, user_follow_count
				append(
					when (sortOrder) {
						SortOrder.POPULARITY -> "view"
						SortOrder.RATING -> "rating"
						else -> "uploaded"
					},
				)
			}
		}
		val ja = webClient.httpGet(url).parseJsonArray()
		val tagsMap = cachedTags ?: loadTags()
		return ja.mapJSON { jo ->
			val slug = jo.getString("slug")
			Manga(
				id = generateUid(slug),
				title = jo.getString("title"),
				altTitle = null,
				url = slug,
				publicUrl = "https://$domain/comic/$slug",
				rating = jo.getDoubleOrDefault("rating", -10.0).toFloat() / 10f,
				isNsfw = false,
				coverUrl = jo.getString("cover_url"),
				largeCoverUrl = null,
				description = jo.getStringOrNull("desc"),
				tags = jo.selectGenres("genres", tagsMap),
				state = runCatching {
					if (jo.getBoolean("translation_completed")) {
						MangaState.FINISHED
					} else {
						MangaState.ONGOING
					}
				}.getOrNull(),
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val domain = domain
		val url = "https://api.$domain/comic/${manga.url}?tachiyomi=true"
		val jo = webClient.httpGet(url).parseJson()
		val comic = jo.getJSONObject("comic")
		return manga.copy(
			title = comic.getString("title"),
			altTitle = null, // TODO
			isNsfw = jo.getBoolean("matureContent") || comic.getBoolean("hentai"),
			description = comic.getStringOrNull("parsed") ?: comic.getString("desc"),
			tags = manga.tags + jo.getJSONArray("genres").mapJSONToSet {
				MangaTag(
					title = it.getString("name"),
					key = it.getString("slug"),
					source = source,
				)
			},
			author = jo.getJSONArray("artists").optJSONObject(0)?.getString("name"),
			chapters = getChapters(comic.getString("hid")),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val jo = webClient.httpGet(
			"https://api.${domain}/chapter/${chapter.url}?tachiyomi=true",
		).parseJson().getJSONObject("chapter")
		return jo.getJSONArray("images").mapJSON {
			val url = it.getString("url")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val sparseArray = cachedTags ?: loadTags()
		val set = ArraySet<MangaTag>(sparseArray.size())
		for (i in 0 until sparseArray.size()) {
			set.add(sparseArray.valueAt(i))
		}
		return set
	}

	private suspend fun loadTags(): SparseArrayCompat<MangaTag> {
		val ja = webClient.httpGet("https://api.${domain}/genre").parseJsonArray()
		val tags = SparseArrayCompat<MangaTag>(ja.length())
		for (jo in ja.JSONIterator()) {
			tags.append(
				jo.getInt("id"),
				MangaTag(
					title = jo.getString("name"),
					key = jo.getString("slug"),
					source = source,
				),
			)
		}
		cachedTags = tags
		return tags
	}

	private suspend fun getChapters(hid: String): List<MangaChapter> {
		val ja = webClient.httpGet(
			url = "https://api.${domain}/comic/$hid/chapters?limit=$CHAPTERS_LIMIT",
		).parseJson().getJSONArray("chapters")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd")
		val list = ja.toJSONList().reversed()

		val chaptersBuilder = ChaptersListBuilder(list.size)
		val branchedChapters = HashMap<String?, HashMap<Pair<String?, String?>, MangaChapter>>()
		for (jo in list) {
			val vol = jo.getStringOrNull("vol")
			val chap = jo.getStringOrNull("chap")
			val volChap = vol to chap
			val locale = Locale.forLanguageTag(jo.getString("lang"))
			val lc = locale.getDisplayName(locale).toTitleCase(locale)
			val branch = (list.indices).firstNotNullOf { i ->
				val b = if (i == 0) lc else "$lc ($i)"
				if (branchedChapters[b]?.get(volChap) == null) b else null
			}
			val chapter = MangaChapter(
				id = generateUid(jo.getLong("id")),
				name = buildString {
					vol?.let { append("Vol ").append(it).append(' ') }
					chap?.let { append("Chap ").append(it) }
					jo.getStringOrNull("title")?.let { append(": ").append(it) }
				},
				number = branchedChapters[branch]?.size?.plus(1) ?: 0,
				url = jo.getString("hid"),
				scanlator = jo.optJSONArray("group_name")?.asIterable<String>()?.joinToString()
					?.takeUnless { it.isBlank() },
				uploadDate = dateFormat.tryParse(jo.getString("created_at").substringBefore('T')),
				branch = branch,
				source = source,
			)
			if (chaptersBuilder.add(chapter)) {
				branchedChapters.getOrPut(branch, ::HashMap)[volChap] = chapter
			}
		}
		return chaptersBuilder.toList()
	}

	private fun JSONObject.selectGenres(name: String, tags: SparseArrayCompat<MangaTag>): Set<MangaTag> {
		val array = optJSONArray(name) ?: return emptySet()
		val res = ArraySet<MangaTag>(array.length())
		for (i in 0 until array.length()) {
			val id = array.getInt(i)
			val tag = tags.get(id) ?: continue
			res.add(tag)
		}
		return res
	}
}
