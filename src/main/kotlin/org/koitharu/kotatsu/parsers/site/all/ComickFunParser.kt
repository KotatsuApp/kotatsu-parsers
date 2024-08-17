package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import androidx.collection.SparseArrayCompat
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * cc
 */

private const val CHAPTERS_LIMIT = 99999

@MangaSourceParser("COMICK_FUN", "ComicK")
internal class ComickFunParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.COMICK_FUN, 20) {

	override val configKeyDomain = ConfigKey.Domain("comick.io", "comick.cc")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.RATING,
	)

	override val availableStates: Set<MangaState> =
		EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED)

	private val tagsArray = SuspendLazy(::loadTags)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val domain = domain
		val url = urlBuilder()
			.host("api.$domain")
			.addPathSegment("v1.0")
			.addPathSegment("search")
			.addQueryParameter("type", "comic")
			.addQueryParameter("tachiyomi", "true")
			.addQueryParameter("limit", pageSize.toString())
			.addQueryParameter("page", page.toString())
		when (filter) {
			is MangaListFilter.Search -> {
				url.addQueryParameter("q", filter.query)
			}

			null -> {
				url.addQueryParameter("sort", "view")
			}

			is MangaListFilter.Advanced -> {
				filter.tags.forEach { tag ->
					url.addQueryParameter("genres", tag.key)
				}
				url.addQueryParameter(
					"sort",
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> "view"
						SortOrder.RATING -> "rating"
						else -> "uploaded"
					},
				)
				filter.states.oneOrThrowIfMany()?.let {
					url.addQueryParameter(
						"status",
						when (it) {
							MangaState.ONGOING -> "1"
							MangaState.FINISHED -> "2"
							MangaState.ABANDONED -> "3"
							MangaState.PAUSED -> "4"
							else -> ""
						},
					)
				}
			}
		}
		val ja = webClient.httpGet(url.build()).parseJsonArray()
		val tagsMap = tagsArray.get()
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
				tags = jo.selectGenres(tagsMap),
				state = when (jo.getIntOrDefault("status", 0)) {
					1 -> MangaState.ONGOING
					2 -> MangaState.FINISHED
					3 -> MangaState.ABANDONED
					4 -> MangaState.PAUSED
					else -> null
				},
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
		var alt = ""
		comic.getJSONArray("md_titles").mapJSON { alt += it.getString("title") + " - " }
		return manga.copy(
			altTitle = alt.ifEmpty { comic.getStringOrNull("title") },
			isNsfw = jo.getBooleanOrDefault("matureContent", false) || comic.getBooleanOrDefault("hentai", false),
			description = comic.getStringOrNull("parsed") ?: comic.getStringOrNull("desc"),
			tags = manga.tags + comic.getJSONArray("md_comic_md_genres").mapJSONToSet {
				val g = it.getJSONObject("md_genres")
				MangaTag(
					title = g.getString("name"),
					key = g.getString("slug"),
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

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val sparseArray = tagsArray.get()
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
					title = jo.getString("name").toTitleCase(Locale.ENGLISH),
					key = jo.getString("slug"),
					source = source,
				),
			)
		}
		return tags
	}

	private suspend fun getChapters(hid: String): List<MangaChapter> {
		val ja = webClient.httpGet(
			url = "https://api.${domain}/comic/$hid/chapters?limit=$CHAPTERS_LIMIT",
		).parseJson().getJSONArray("chapters")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd")
		return ja.toJSONList().reversed().mapChapters { _, jo ->
			val vol = jo.getIntOrDefault("vol", 0)
			val chap = jo.getFloatOrDefault("chap", 0f)
			val locale = Locale.forLanguageTag(jo.getString("lang"))
			val group = jo.optJSONArray("group_name")?.joinToString(", ")
			val branch = buildString {
				append(locale.getDisplayName(locale).toTitleCase(locale))
				if (!group.isNullOrEmpty()) {
					append(" (")
					append(group)
					append(')')
				}
			}
			MangaChapter(
				id = generateUid(jo.getLong("id")),
				name = buildString {
					if (vol > 0) {
						append("Vol ").append(vol).append(' ')
					}
					append("Chap ").append(chap)
					jo.getStringOrNull("title")?.let { append(": ").append(it) }
				},
				number = chap,
				volume = vol,
				url = jo.getString("hid"),
				scanlator = jo.optJSONArray("group_name")?.asIterable<String>()?.joinToString()
					?.takeUnless { it.isBlank() },
				uploadDate = dateFormat.tryParse(jo.getString("created_at").substringBefore('T')),
				branch = branch,
				source = source,
			)
		}


		/*val chaptersBuilder = ChaptersListBuilder(list.size)
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
				number = branchedChapters[branch]?.size?.plus(1) ?: 1,
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
		return chaptersBuilder.toList()*/
	}

	private fun JSONObject.selectGenres(tags: SparseArrayCompat<MangaTag>): Set<MangaTag> {
		val array = optJSONArray("genres") ?: return emptySet()
		val res = ArraySet<MangaTag>(array.length())
		for (i in 0 until array.length()) {
			val id = array.getInt(i)
			val tag = tags[id] ?: continue
			res.add(tag)
		}
		return res
	}

	private fun JSONArray.joinToString(separator: String): String {
		return (0 until length()).joinToString(separator) { i -> getString(i) }
	}
}
