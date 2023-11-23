package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.asIterable
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("YURINEKO", "YuriNeko", "vi", ContentType.HENTAI)
class YurinekoParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.YURINEKO, 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("yurineko.net")

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED)

	private val apiDomain
		get() = "api.$domain"

	override suspend fun getDetails(manga: Manga): Manga {
		val response = webClient.httpGet(manga.url.toAbsoluteUrl(apiDomain)).parseJson()
		val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
		return manga.copy(
			chapters = response.getJSONArray("chapters")
				.toJSONList()
				.mapChapters(true) { i, jo ->
					val mangaId = jo.getInt("mangaID")
					val chapterId = jo.getInt("id")
					MangaChapter(
						id = generateUid(chapterId.toLong()),
						name = jo.getString("name"),
						number = i + 1,
						scanlator = null,
						url = "/read/$mangaId/$chapterId",
						uploadDate = df.tryParse(jo.getString("date")),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val listUrl = when {
			!query.isNullOrEmpty() -> "/search?query=${query.urlEncoded()}&page=$page"
			tags.isNullOrEmpty() -> "/lastest2?page=$page"
			tags.size == 1 -> "/searchType?type=tag&id=${tags.first().key}&page=$page"
			else -> {
				// Sort order is different when filter with multiple tags
				val tagKeys = tags.joinToString(separator = ",") { it.key }
				"/advancedSearch?genre=$tagKeys&notGenre=&sort=7&minChapter=1&status=0&page=$page"
			}
		}
		val jsonResponse = webClient.httpGet(listUrl.toAbsoluteUrl(apiDomain)).parseJson()
		return jsonResponse.getJSONArray("result")
			.mapJSON { jo ->
				val id = jo.getLong("id")
				val relativeUrl = "/manga/$id"
				Manga(
					id = generateUid(id),
					title = jo.getString("originalName"),
					altTitle = jo.getStringOrNull("otherName"),
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = jo.getString("thumbnail"),
					tags = jo.getJSONArray("tag").mapJSONToSet { tag ->
						MangaTag(
							title = tag.getString("name"),
							key = tag.getInt("id").toString(),
							source = source,
						)
					},
					state = when (jo.getInt("status")) {
						2 -> MangaState.FINISHED
						1, 3, 4 -> MangaState.ONGOING
						5, 6, 7 -> MangaState.ABANDONED
						else -> null
					},
					author = jo.getJSONArray("author")
						.mapJSON { author -> author.getString("name") }
						.joinToString { it },
					description = jo.getStringOrNull("description"),
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val jsonData = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
			.requireElementById("__NEXT_DATA__")
			.data()
		return JSONObject(jsonData).getJSONObject("props")
			.getJSONObject("pageProps")
			.getJSONObject("chapterData")
			.getJSONArray("url")
			.asIterable<String>()
			.map { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return webClient.httpGet("https://$apiDomain/tag/find?query=")
			.parseJsonArray()
			.mapJSONToSet { jo ->
				MangaTag(
					key = jo.getInt("id").toString(),
					title = jo.getString("name"),
					source = source,
				)
			}
	}
}
