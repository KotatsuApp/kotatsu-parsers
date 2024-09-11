package org.koitharu.kotatsu.parsers.site.ru

import androidx.collection.ArrayMap
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.util.*

@MangaSourceParser("DESUME", "Desu", "ru")
internal class DesuMeParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.DESUME, 20) {

	override val configKeyDomain = ConfigKey.Domain("desu.win", "desu.me")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", UserAgents.KOTATSU)
		.build()

	private val tagsCache = SuspendLazy(::fetchTags)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query != null && page != searchPaginator.firstPage) {
			return emptyList()
		}
		val domain = domain
		val url = buildString {
			append("https://")
			append(domain)
			append("/manga/api/?limit=20&order=")
			append(getSortKey(sortOrder))
			append("&page=")
			append(page)
			if (!tags.isNullOrEmpty()) {
				append("&genres=")
				appendAll(tags, ",") { it.key }
			}
			if (query != null) {
				append("&search=")
				append(query)
			}
		}
		val json = webClient.httpGet(url).parseJson().getJSONArray("response")
			?: throw ParseException("Invalid response", url)
		val total = json.length()
		val list = ArrayList<Manga>(total)
		val tagsMap = tagsCache.tryGet().getOrNull()
		for (i in 0 until total) {
			val jo = json.getJSONObject(i)
			val cover = jo.getJSONObject("image")
			val id = jo.getLong("id")
			val genres = jo.getString("genres").split(',')
			list += Manga(
				url = "/manga/api/$id",
				publicUrl = jo.getString("url"),
				source = MangaParserSource.DESUME,
				title = jo.getString("russian"),
				altTitle = jo.getString("name"),
				coverUrl = cover.getString("preview"),
				largeCoverUrl = cover.getString("original"),
				state = when (jo.getString("status")) {
					"ongoing" -> MangaState.ONGOING
					"released" -> MangaState.FINISHED
					else -> null
				},
				rating = jo.getDouble("score").toFloat().coerceIn(0f, 1f),
				id = generateUid(id),
				isNsfw = false,
				tags = if (!tagsMap.isNullOrEmpty()) {
					genres.mapNotNullToSet { g ->
						tagsMap[g.trim().toTitleCase()]
					}
				} else {
					emptySet()
				},
				author = null,
				description = jo.getString("description"),
			)
		}
		return list
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(url).parseJson().getJSONObject("response")
			?: throw ParseException("Invalid response", url)
		val baseChapterUrl = manga.url + "/chapter/"
		val chaptersList = json.getJSONObject("chapters").getJSONArray("list")
		val totalChapters = chaptersList.length()
		return manga.copy(
			tags = json.getJSONArray("genres").mapJSONToSet {
				MangaTag(
					key = it.getString("text"),
					title = it.getString("russian").toTitleCase(),
					source = manga.source,
				)
			},
			publicUrl = json.getString("url"),
			description = json.getString("description"),
			chapters = chaptersList.mapJSON { jo ->
				val chid = jo.getLong("id")
				val volume = jo.getIntOrDefault("vol", 0)
				val number = jo.getFloatOrDefault("ch", 0f)
				MangaChapter(
					id = generateUid(chid),
					source = manga.source,
					url = "$baseChapterUrl$chid",
					uploadDate = jo.getLong("date") * 1000,
					name = jo.getStringOrNull("title") ?: buildString {
						append("Том ")
						append(volume)
						append(" Глава ")
						append(number)
						removeTrailingZero()
					},
					volume = volume,
					number = number,
					scanlator = null,
					branch = null,
				)
			}.reversed(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(fullUrl)
			.parseJson()
			.getJSONObject("response") ?: throw ParseException("Invalid response", fullUrl)
		return json.getJSONObject("pages").getJSONArray("list").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getLong("id")),
				preview = null,
				source = chapter.source,
				url = jo.getString("img"),
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return tagsCache.get().values.toSet()
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "popular"
			SortOrder.UPDATED -> "updated"
			SortOrder.NEWEST -> "id"
			else -> "updated"
		}

	private suspend fun fetchTags(): Map<String, MangaTag> {
		val doc = webClient.httpGet("https://$domain/manga/").parseHtml()
		val root = doc.body().requireElementById("animeFilter")
			.selectFirstOrThrow(".catalog-genres")
		val li = root.select("li")
		val result = ArrayMap<String, MangaTag>(li.size)
		li.forEach {
			val input = it.selectFirstOrThrow("input")
			val tag = MangaTag(
				source = source,
				key = input.attr("data-genre-slug").ifEmpty {
					it.parseFailed("data-genre-slug is empty")
				},
				title = input.attr("data-genre-name").toTitleCase().ifEmpty {
					it.parseFailed("data-genre-name is empty")
				},
			)
			result[tag.title] = tag
		}
		return result
	}
}
