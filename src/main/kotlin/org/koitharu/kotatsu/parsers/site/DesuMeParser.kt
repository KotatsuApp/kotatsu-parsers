package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.util.*

@MangaSourceParser("DESUME", "Desu.me", "ru")
internal class DesuMeParser(override val context: MangaLoaderContext) : PagedMangaParser(MangaSource.DESUME, 20) {

	override val configKeyDomain = ConfigKey.Domain("desu.me", null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query != null && page != searchPaginator.firstPage) {
			return emptyList()
		}
		val domain = getDomain()
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
		val json = context.httpGet(url).parseJson().getJSONArray("response") ?: parseFailed("Invalid response")
		val total = json.length()
		val list = ArrayList<Manga>(total)
		for (i in 0 until total) {
			val jo = json.getJSONObject(i)
			val cover = jo.getJSONObject("image")
			val id = jo.getLong("id")
			list += Manga(
				url = "/manga/api/$id",
				publicUrl = jo.getString("url"),
				source = MangaSource.DESUME,
				title = jo.getString("russian"),
				altTitle = jo.getString("name"),
				coverUrl = cover.getString("preview"),
				largeCoverUrl = cover.getString("original"),
				state = when {
					jo.getInt("ongoing") == 1 -> MangaState.ONGOING
					else -> null
				},
				rating = jo.getDouble("score").toFloat().coerceIn(0f, 1f),
				id = generateUid(id),
				isNsfw = false,
				tags = emptySet(),
				author = null,
				description = jo.getString("description"),
			)
		}
		return list
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.url.toAbsoluteUrl(getDomain())
		val json = context.httpGet(url).parseJson().getJSONObject("response")
			?: throw ParseException("Invalid response")
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
			chapters = chaptersList.mapJSONIndexed { i, it ->
				val chid = it.getLong("id")
				val volChap = "Том " + it.optString("vol", "0") + ". " + "Глава " + it.optString("ch", "0")
				val title = it.optString("title", "null").takeUnless { it == "null" }
				MangaChapter(
					id = generateUid(chid),
					source = manga.source,
					url = "$baseChapterUrl$chid",
					uploadDate = it.getLong("date") * 1000,
					name = if (title.isNullOrEmpty()) volChap else "$volChap: $title",
					number = totalChapters - i,
					scanlator = null,
					branch = null,
				)
			}.reversed(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain())
		val json = context.httpGet(fullUrl)
			.parseJson()
			.getJSONObject("response") ?: throw ParseException("Invalid response")
		return json.getJSONObject("pages").getJSONArray("list").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getLong("id")),
				referer = fullUrl,
				preview = null,
				source = chapter.source,
				url = jo.getString("img"),
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}/manga/").parseHtml()
		val root = doc.body().getElementById("animeFilter")
			?.selectFirst(".catalog-genres") ?: throw ParseException("Root not found")
		return root.select("li").mapToSet {
			val input = it.selectFirst("input") ?: parseFailed()
			MangaTag(
				source = source,
				key = input.attr("data-genre-slug").ifEmpty {
					parseFailed("data-genre-slug is empty")
				},
				title = input.attr("data-genre-name").toTitleCase().ifEmpty {
					parseFailed("data-genre-name is empty")
				},
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "popular"
			SortOrder.UPDATED -> "updated"
			SortOrder.NEWEST -> "id"
			else -> "updated"
		}
}