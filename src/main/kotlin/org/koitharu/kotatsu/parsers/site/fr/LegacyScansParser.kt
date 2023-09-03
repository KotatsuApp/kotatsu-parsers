package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
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

@MangaSourceParser("LEGACY_SCANS", "Legacy Scans", "fr")
internal class LegacyScansParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.LEGACY_SCANS, 18) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("legacy-scans.com")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_MOBILE)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val end = page * pageSize
		val start = end - (pageSize - 1)
		val url = if (!query.isNullOrEmpty()) {
			if (page > 1) {
				return emptyList()
			}
			buildString {
				append("https://api.$domain/misc/home/search?title=")
				append(query.urlEncoded())
			}
		} else {
			buildString {
				append("https://api.$domain/misc/comic/search/query?status=&order=&genreNames=")
				if (!tags.isNullOrEmpty()) {
					for (tag in tags) {
						append(tag.key)
						append(',')
					}
				}
				append("&type=&start=")
				append(start)
				append("&end=")
				append(end)
			}

		}
		val json = webClient.httpGet(url).parseJson()
		return if (!query.isNullOrEmpty()) {
			json.getJSONArray("results").mapJSON { j ->
				val slug = j.getString("slug")
				val urlManga = "https://$domain/comics/$slug"
				Manga(
					id = generateUid(urlManga),
					title = j.getString("title"),
					altTitle = null,
					url = urlManga,
					publicUrl = urlManga,
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = "",
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
		} else {
			json.getJSONArray("comics").mapJSON { j ->
				val slug = j.getString("slug")
				val urlManga = "https://$domain/comics/$slug"
				Manga(
					id = generateUid(urlManga),
					title = j.getString("title"),
					altTitle = null,
					url = urlManga,
					publicUrl = urlManga,
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = "https://api.$domain/" + j.getString("cover"),
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
		}

	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

		return manga.copy(
			altTitle = null,
			tags = root.select("div.serieGenre span").mapNotNullToSet { span ->
				MangaTag(
					key = span.text(),
					title = span.text(),
					source = source,
				)
			},
			coverUrl = root.selectFirstOrThrow("div.serieImg img").attr("src"),
			author = root.select("div.serieAdd p:contains(Auteur:) strong").text(),
			description = root.selectFirst("div.serieDescription div")?.html(),
			chapters = root.select("div.chapterList a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirstOrThrow("span").text()
					val dateText = a.selectLast("span")?.text() ?: "0"
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i + 1,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.readerComics img").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/comics").parseHtml()
		val script = doc.requireElementById("__NUXT_DATA__").data()
			.substringAfterLast("\"genres\"").substringBeforeLast("\"comics\"")
			.split("\",\"").drop(1)

		return script.mapNotNullToSet { tag ->
			MangaTag(
				key = tag.substringBeforeLast("\",{"),
				title = tag.substringBeforeLast("\",{"),
				source = source,
			)
		}
	}
}
