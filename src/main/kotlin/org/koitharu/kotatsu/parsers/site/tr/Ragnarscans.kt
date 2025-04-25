package org.koitharu.kotatsu.parsers.site.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacySinglePageMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("RAGNARSCANS", "RagnarScans", "tr")
internal class RagnarScans(context: MangaLoaderContext) :
	LegacySinglePageMangaParser(context, MangaParserSource.RAGNARSCANS) {

	override val configKeyDomain = ConfigKey.Domain("ragnarscans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val results = mutableListOf<Manga>()
		var page = 1
		var hasNext = true
		while (hasNext) {
			val url = if (filter.query.isNullOrBlank()) {
				buildListUrl(page)
			} else {
				buildSearchUrl(page, filter.query)
			}
			val doc = webClient.httpGet(url).parseHtml()
			val mangaDivs = if (filter.query.isNullOrBlank()) {
				doc.select(".col-6.col-md-3.badge-pos-2")
			} else {
				doc.select(".row.c-tabs-item__content")
			}
			if (mangaDivs.isEmpty()) break
			for (div in mangaDivs) {
				val a = if (filter.query.isNullOrBlank()) {
					div.selectFirstOrThrow(".item-thumb a")
				} else {
					div.selectFirstOrThrow(".tab-thumb a")
				}
				val href = a.attrAsRelativeUrl("href")
				val title = if (filter.query.isNullOrBlank()) {
					div.selectFirstOrThrow(".post-title.font-title a").text()
				} else {
					div.selectFirstOrThrow(".post-title a").text()
				}
				val img = if (filter.query.isNullOrBlank()) {
					div.selectFirstOrThrow(".item-thumb img").src()?.toAbsoluteUrl(domain).orEmpty()
				} else {
					div.selectFirstOrThrow(".tab-thumb img").src()?.toAbsoluteUrl(domain).orEmpty()
				}
				results.add(
					Manga(
						id = generateUid(href),
						title = title,
						altTitles = emptySet(),
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						contentRating = null,
						coverUrl = img,
						tags = emptySet(),
						state = null,
						authors = emptySet(),
						source = source,
					)
				)
			}
			hasNext = if (filter.query.isNullOrBlank()) {
				doc.selectFirst(".nav-previous") != null && mangaDivs.size >= 20
			} else {
				doc.selectFirst(".pagination .next") != null || doc.selectFirst(".c-pagination .next") != null
			}
			page++
			if (filter.query.isNullOrBlank()) {
				if (mangaDivs.size < 20) break
			}
		}
		return results
	}

	private fun buildListUrl(page: Int): String {
		return buildString {
			append("https://")
			append(domain)
			append("/manga/")
			if (page > 1) append("page/$page/")
		}
	}

	private fun buildSearchUrl(page: Int, query: String): String {
		return buildString {
			append("https://")
			append(domain)
			append("/page/$page/?s=")
			append(query.urlEncoded())
			append("&post_type=wp-manga")
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
        val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
        val author = doc.select(".author-content a").firstOrNull()?.textOrNull()
        val genres = doc.select(".genres-content a").mapNotNull { it.textOrNull() }.toSet()
        val summaryBlocks = doc.select(".summary-content")
        val statusText = summaryBlocks.getOrNull(3)?.textOrNull()?.trim()
        val description = doc.selectFirstOrThrow(".summary__content.show-more p").html()
        val state = when (statusText) {
            "Devam Ediyor" -> MangaState.ONGOING
            "Tamamlandı" -> MangaState.FINISHED
            else -> null
        }
        val chapters = doc.select("ul.main.version-chap.no-volumn.active li.wp-manga-chapter").mapIndexed { i, li ->
            val a = li.selectFirstOrThrow("a")
            val url = a.attrAsRelativeUrl("href")
            val title = a.text()
            val dateStr = li.select(".chapter-release-date i").firstOrNull()?.textOrNull()
            val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr"))
            MangaChapter(
                id = generateUid(url),
                title = title,
                number = (i + 1).toFloat(),
                volume = 0,
                url = url,
                scanlator = null,
                uploadDate = dateFormat.tryParse(dateStr),
                branch = null,
                source = source,
            )
        }.reversed()
    
        return manga.copy(
            state = state,
            authors = setOfNotNull(author),
            description = description,
            chapters = chapters,
        )
    }
    
    

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".reading-content img").map { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
