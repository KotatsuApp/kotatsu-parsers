package org.koitharu.kotatsu.parsers.site.es

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
import java.util.*

@MangaSourceParser("TEMPLESCANESP", "TempleScanEsp", "es", ContentType.HENTAI)
internal class TempleScanEsp(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.TEMPLESCANESP, pageSize = 15) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST, SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("templescanesp.net")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			if (sortOrder == SortOrder.NEWEST) {
				append("/comics")
				append("?page=")
				append(page.toString())
			} else {
				if (page > 1) {
					return emptyList()
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.grid figure").ifEmpty {
			doc.requireElementById("projectsDiv").select("figure")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("figcaption")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = getChapters(doc)
		manga.copy(
			description = doc.requireElementById("section-sinopsis").html(),
			chapters = chaptersDeferred,
		)
	}

	private fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select("div.grid-capitulos div.contenedor-capitulo-miniatura")
			.mapChapters(reversed = true) { i, div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				val date = parseUploadDate(div.selectFirstOrThrow("time").text())
				MangaChapter(
					id = generateUid(href),
					name = div.requireElementById("name").text(),
					number = i + 1,
					url = href,
					uploadDate = date,
					source = source,
					scanlator = null,
					branch = null,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("main.contenedor-imagen img").map { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseUploadDate(timeStr: String?): Long {
		timeStr ?: return 0
		val timeWords = timeStr.split(' ')
		if (timeWords.size != 3) return 0
		val timeWord = timeWords[1]
		val timeAmount = timeWords[0].toIntOrNull() ?: return 0
		val timeUnit = when (timeWord) {
			"minute", "minutes" -> Calendar.MINUTE
			"hour", "hours" -> Calendar.HOUR
			"day", "days" -> Calendar.DAY_OF_YEAR
			"week", "weeks" -> Calendar.WEEK_OF_YEAR
			"month", "months" -> Calendar.MONTH
			"year", "years" -> Calendar.YEAR
			else -> return 0
		}
		val cal = Calendar.getInstance()
		cal.add(timeUnit, -timeAmount)
		return cal.time.time
	}
}
