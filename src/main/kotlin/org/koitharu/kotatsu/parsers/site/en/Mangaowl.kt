package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAOWL", "Mangaowl", "en")
internal class Mangaowl(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.MANGAOWL, pageSize = 24) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
		SortOrder.RATING,
	)

	override val configKeyDomain = ConfigKey.Domain("mangaowl.to")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val sort = when (sortOrder) {
			SortOrder.POPULARITY -> "view_count"
			SortOrder.UPDATED -> "-modified_at"
			SortOrder.NEWEST -> "created_at"
			SortOrder.RATING -> "rating"
			else -> "modified_at"
		}
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!query.isNullOrEmpty() -> {
					append("/8-search")
					append("?q=")
					append(query.urlEncoded())
					append("&page=")
					append(page.toString())
				}

				!tags.isNullOrEmpty() -> {
					append("/8-genres/")
					for (tag in tags) {
						append(tag.key)
					}
					append("?page=")
					append(page.toString())
				}

				else -> {

					append("/8-comics")
					append("?page=")
					append(page.toString())
					append("&ordering=")
					append(sort)
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.manga-item.column").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("a.one-line")?.text().orEmpty(),
				altTitle = null,
				rating = div.select("span").last()?.text()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = false,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/8-genres").parseHtml()
		return doc.select("div.genres-container span.genre-item a").mapNotNullToSet { a ->
			val key = a.attr("href").substringAfterLast("/")
			MangaTag(
				key = key,
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		manga.copy(
			tags = doc.body().select("div.comic-attrs div.column.my-2:contains(Genres) a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase().replace(",", ""),
					source = source,
				)
			},
			description = doc.select("span.story-desc").html(),
			state = when (doc.select("div.section-status:contains(Status) span").last()?.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			chapters = getChapters(manga.url, doc),
		)
	}

	private fun getChapters(mangaUrl: String, doc: Document): List<MangaChapter> {

		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", sourceLocale)

		val script = doc.selectFirstOrThrow("script:containsData(chapters:)")
		val json =
			script.data().substringAfter("chapters:[").substringBeforeLast(')').substringBefore("],latest_chapter:")
				.split("},")
		val slug = mangaUrl.substringAfterLast("/")

		val chapter = ArrayList<MangaChapter>()
		val num = 0
		json.map { t ->
			if (t.contains("Chapter")) {
				val id = t.substringAfter("id:").substringBefore(",created_at")
				val url = "/reading/$slug/$id"

				val date = t.substringAfter("created_at:\"").substringBefore("\"")
				val name = t.substringAfter("name:\"").substringBefore("\"")
				chapter.add(
					MangaChapter(
						id = generateUid(url),
						name = name,
						number = num + 1,
						url = url,
						uploadDate = dateFormat.tryParse(date),
						source = source,
						scanlator = null,
						branch = null,
					),
				)
			}
		}

		// last chapter
		val id = script.data().substringAfter("Sign in\",").substringBefore(",\"").split(",").last()
		val url = "/reading/$slug/$id"
		val date = script.data().substringAfter("$id,\"").substringBefore("\",")
		val name = script.data().substringAfter("$date\",\"").substringBefore("\",")
		chapter.add(
			MangaChapter(
				id = generateUid(url),
				name = name,
				number = num + 1,
				url = url,
				uploadDate = dateFormat.tryParse(date),
				source = source,
				scanlator = null,
				branch = null,
			),
		)

		return chapter
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = chapter.url.substringAfterLast("/")

		val json = webClient.httpGet("https://api.mangaowl.to/v1/chapters/$id/images?page_size=100").parseJson()
		return json.getJSONArray("results").mapJSON { jo ->
			MangaPage(
				id = generateUid(jo.getString("image")),
				preview = null,
				source = chapter.source,
				url = jo.getString("image"),
			)
		}
	}
}
