package org.koitharu.kotatsu.parsers.site.vi

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken("Need to remake parser")
@MangaSourceParser("TRUYENHENTAI18", "TruyenHentai18", "vi", ContentType.HENTAI)
internal class TruyenHentai18(context: MangaLoaderContext) : LegacyPagedMangaParser(context, MangaParserSource.TRUYENHENTAI18, 18) {

	override val configKeyDomain = ConfigKey.Domain("truyenhentai18.app")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
        SortOrder.POPULARITY,
        SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
            isSearchWithFiltersSupported = false,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = emptySet(), // cant find any URLs for fetch tags
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = when {
			!filter.query.isNullOrEmpty() -> {
				buildString {
					append(domain)
					append("/page/")
					append(page)
					append("?s=")
					append(filter.query.urlEncoded())
				}
			}
			!filter.author.isNullOrEmpty() -> {
				buildString {
					append(domain)
					append("/artist/")
					append(filter.author.urlEncoded())
				}
			}
			else -> {
				buildString {
					append(domain)
					if (filter.tags.isNotEmpty()) {
						append("/category/")
						append(filter.tags.first().key)
					} else {
						append(
							when (order) {
								SortOrder.UPDATED -> "/moi-cap-nhat"
								SortOrder.POPULARITY -> "/xem-nhieu-nhat"
                                SortOrder.RATING -> "/truyen-de-xuat"
								else -> "/moi-cap-nhat"
							}
						)
					}
					if (page > 1) {
						append("/page/")
						append(page)
					}
				}
			}
		}

		val doc = webClient.httpGet("https://$url").parseHtml()
		return when {
			!filter.query.isNullOrEmpty() -> parseSearchList(doc)
			!filter.author.isNullOrEmpty() -> parseSearchList(doc)
			else -> parseMangaList(doc)
		}
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("a.item-cover.ms-3.me-3").mapNotNull { element ->
			val href = element.attrAsRelativeUrl("href") ?: return@mapNotNull null
			val img = element.selectFirst("img") ?: return@mapNotNull null
			val coverUrl = img.attr("data-src").orEmpty()
			val title = img.attr("alt").orEmpty()
			
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	private fun parseSearchList(doc: Document): List<Manga> {
		return doc.select("div.card.mb-3.small-item").mapNotNull { element ->
			val href = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapNotNull null
			val img = element.selectFirst("img") ?: return@mapNotNull null
			val coverUrl = img.attr("data-src").orEmpty()
			val title = img.attr("alt").orEmpty()
			
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val rating = doc.selectFirst("div.kksr-stars")?.attr("data-rating")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN
        val description = doc.selectFirst("div.mt-3.desc-text")?.text()
		
		val author = doc.select("div.attr-item").firstOrNull { 
			it.selectFirst("b")?.text() == "Tác giả:" 
		}?.selectFirst("a")?.text()

		val tags = doc.select("ul.post-categories li a").mapNotNull { element ->
			val name = element.text()
			val key = element.attr("href").substringAfter("/category/")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}.toSet()

		val chapters = doc.select("div.p-2.d-flex.flex-column.flex-md-row.item").reversed()
			.mapChapters(reversed = false) { i, e ->
				val name = e.selectFirst("b")?.text() ?: ""
				val href = e.selectFirst("a")?.attrAsRelativeUrl("href") ?: ""
				val dateText = e.selectFirst("i.ps-3")?.text()
				MangaChapter(
					id = generateUid(href),
					title = name,
					url = href,
					number = i + 1f,
					volume = 0,
					uploadDate = parseChapterDate(dateText),
					scanlator = null,
					branch = null,
					source = source,
				)
			}

		return manga.copy(
			rating = rating,
			authors = setOfNotNull(author),
			description = description,
			chapters = chapters,
			tags = tags,
			contentRating = ContentRating.ADULT,
		)
	}

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div#viewer p img").mapNotNull { img -> // Need debug
			val url = img.attr("src") ?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateText: String?): Long {
		if (dateText == null) return 0

		val relativeTimePattern = Regex("(\\d+)\\s*(ngày|tuần|tháng|năm) trước")
		val absoluteTimePattern = Regex("(\\d{2}-\\d{2}-\\d{4})")

		return when {
			dateText.contains("ngày trước") -> {
				val match = relativeTimePattern.find(dateText)
				val days = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - days * 86400 * 1000
			}

			dateText.contains("tuần trước") -> {
				val match = relativeTimePattern.find(dateText)
				val weeks = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - weeks * 7 * 86400 * 1000
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
				val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
				formatter.tryParse(dateText)
			}

			else -> 0L
		}
	}
}
