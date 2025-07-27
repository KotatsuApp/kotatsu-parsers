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
		return listOf(getMangaDetails())
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		return manga.copy(
			description = doc.selectFirst("div.entry-content")?.html(),
			state = MangaState.ONGOING,
			authors = setOf("Son Jae Ho & ZHENA"),
			tags = setOf(
				MangaTag("aksiyon", "Aksiyon", source),
				MangaTag("drama", "Drama", source),
				MangaTag("komedi", "Komedi", source),
				MangaTag("süper-güçler", "Süper Güçler", source)
			),
			chapters = doc.select("div.episode-list > ul > li > a").map { a ->
				val href = a.attrAsRelativeUrl("href")

				MangaChapter(
					id = generateUid(href),
					title = a.selectFirst("span.episode-name")?.text()
						?: "Bölüm ${parseChapterNumberFromUrl(href)}",
					number = parseChapterNumberFromUrl(href).toFloat(),
					url = href,
					uploadDate = parseChapterDate(a.selectFirst("span.episode-date")?.text()),
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

	private fun getMangaDetails(): Manga {
		return Manga(
			id = generateUid(mangaId),
			url = "/eleeced-bolum-$mangaId",
			publicUrl = "https://$domain/eleceed/$mangaId",
			coverUrl = "https://$domain/wp-content/uploads/eleceed-cover.jpg",
			title = "Eleceed",
			altTitles = emptySet(),
			rating = RATING_UNKNOWN,
			tags = emptySet(),
			authors = setOf("Son Jae Ho", "ZHENA"),
			state = MangaState.ONGOING,
			source = source,
			contentRating = ContentRating.ADULT
		)
	}

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
