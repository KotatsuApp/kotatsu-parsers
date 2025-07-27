package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue
import org.koitharu.kotatsu.parsers.Broken

@Broken("Fix chapter number + Remove unused functions")
@MangaSourceParser("ELECEEDTURKIYE", "Eleceed Türkiye", "tr")
internal class EleceedTurkiye(context: MangaLoaderContext):
	SinglePageMangaParser(context, MangaParserSource.ELECEEDTURKIYE) {

	override val configKeyDomain = ConfigKey.Domain(
		"eleceedturkiye.com",
		"www.eleceedturkiye.com"
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val raw = webClient.httpGet("https://$domain").parseHtml()
		val coverUrl = raw.select("div.thumb img").attr("src")
		return listOf(
			Manga(
				id = generateUid(mangaId),
				url = "https://$domain/",
				publicUrl = "https://$domain/",
				coverUrl = coverUrl,
				title = "Eleceed",
				altTitles = emptySet(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				authors = setOf("Son Jae Ho", "ZHENA"),
				state = MangaState.ONGOING,
				source = source,
				contentRating = ContentRating.ADULT
			)
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url).parseHtml()
		return manga.copy(
			description = doc.selectFirst("div.entry-content")?.html(),
			tags = setOf(
				MangaTag("Aksiyon", "", source),
				MangaTag("Drama", "", source),
				MangaTag("Komedi", "", source),
				MangaTag("Süper Güçler", "", source)
			),
			chapters = doc.select("div.eph-num").map { a ->
				val href = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(href),
					title = a.selectFirst("span.chapternum")?.text()
						?: "Bölüm ${parseChapterNumberFromUrl(href)}",
					number = parseChapterNumberFromUrl(href).toFloat(),
					url = href,
					uploadDate = parseChapterDate(a.selectFirst("span.chapterdate")?.text()),
					scanlator = null,
					branch = null,
					source = source,
					volume = 0,
				)
			}.sortedByDescending { it.number }
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.reading-content > img").map { img ->
			val src = img.attr("src").ifEmpty { img.attr("data-src") }
			MangaPage(
				id = generateUid(src),
				url = src.toAbsoluteUrl(domain),
				preview = null,
				source = source,
			)
		}
	}

	private val mangaId = "eleceed"
	private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr"))

	private fun parseChapterDate(dateString: String?): Long {
		return dateString?.let {
			try {
				dateFormat.parse(it)?.time ?: 0
			} catch (e: Exception) {
				0
			}
		} ?: 0
	}

	private fun parseChapterNumberFromUrl(url: String): Int {
		return try {
			url.substringAfterLast('-')
				.substringBefore('/')
				.toInt()
		} catch (e: Exception) {
			url.hashCode().absoluteValue
		}
	}
}
