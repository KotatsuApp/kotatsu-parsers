package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.jsoup.nodes.Document
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NHATTRUYENVN", "NhatTruyenVN", "vi")
internal class NhatTruyenVN(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NHATTRUYENVN, "nhattruyenv.com", 36) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("nhattruyenv.com", "www.nhattruyenss.net")

	private val selectChap = "ul#asc li.row:not(.heading)"

	private fun getChaps(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChap).mapChapters(reversed = false) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val chapterNumber = a.text().substringAfter("Chapter ").substringBefore(" ").toFloatOrNull() ?: (i + 1f)
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = chapterNumber,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChaps(doc) }
		val tagMap = getOrCreateTagMap()
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet { tagMap[it.text()] }
		manga.copy(
			description = doc.selectFirst(selectDesc)?.html(),
			altTitle = doc.selectFirst("h2.other-name")?.textOrNull(),
			author = doc.body().selectFirst(selectAut)?.textOrNull(),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await(),
		)
	}

	private fun parseChapterDate(dateText: String?): Long {
		if (dateText == null) return 0

		val relativeTimePattern = Regex("(\\d+)\\s*(phút|giờ|ngày|tháng|năm) trước")
		val absoluteTimePattern = Regex("(\\d{2}/\\d{2}/\\d{4})")

		return when {
			dateText.contains("phút trước") -> {
				val match = relativeTimePattern.find(dateText)
				val minutes = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - minutes * 60 * 1000
			}

			dateText.contains("giờ trước") -> {
				val match = relativeTimePattern.find(dateText)
				val hours = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - hours * 3600 * 1000
			}

			dateText.contains("ngày trước") -> {
				val match = relativeTimePattern.find(dateText)
				val days = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - days * 86400 * 1000
			}

			dateText.contains("tháng trước") -> {
				val match = relativeTimePattern.find(dateText)
				val months = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - months * 30 * 86400 * 1000
			}

			dateText.contains("năm trước") -> {
				val match = relativeTimePattern.find(dateText)
				val years = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - years * 365 * 86400 * 1000
			}

			absoluteTimePattern.matches(dateText) -> {
				val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
				try {
					val parsedDate = formatter.parse(dateText)
					parsedDate?.time ?: 0L
				} catch (e: Exception) {
					0L
				}
			}

			else -> 0L
		}
	}
}
