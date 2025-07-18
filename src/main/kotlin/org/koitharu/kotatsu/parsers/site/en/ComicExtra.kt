package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("COMICEXTRA", "ComicExtra", "en", ContentType.COMICS)
internal class ComicExtra(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.COMICEXTRA, 36) {

	override val configKeyDomain = ConfigKey.Domain("azcomix.me")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/")
			when {
				!filter.query.isNullOrEmpty() -> {
					append("search?keyword=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page.toString())
					}
				}

				else -> {
					when (order) {
						SortOrder.POPULARITY -> append("popular-comics")
						SortOrder.UPDATED -> append("new-comics")
						SortOrder.NEWEST -> append("recent-comics")
						else -> append("new-comics")
					}
					if (page > 1) {
						append("?page=")
						append(page.toString())
					}
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.eg-box").map { div ->
			val href = div.selectFirstOrThrow("a.eg-image").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirstOrThrow("a.egb-serie").text(),
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
				tags = div.select("div.egb-details a").mapToSet { a ->
					MangaTag(
						key = a.attr("href").substringAfterLast('/'),
						title = a.text(),
						source = source,
					)
				},
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/genre/marvel").parseHtml()
		return doc.select("ul.lf-list li a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast('/'),
				title = a.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val author = doc.selectFirst("table.full-table tr:contains(Author:) td:nth-child(2)")?.textOrNull()
		return manga.copy(
			altTitles = setOfNotNull(doc.selectFirstOrThrow("div.anime-top h1.title").textOrNull()),
			state = when (doc.selectFirstOrThrow("ul.anime-genres li.status a").text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			tags = doc.select("ul.anime-genres li a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text().toTitleCase(sourceLocale),
					source = source,
				)
			},
			authors = setOfNotNull(author),
			description = doc.selectFirstOrThrow("div.detail-desc-content p").html(),
			chapters = doc.select("ul.basic-list li").let { elements ->
				elements.mapChapters { i, li ->
					val a = li.selectFirstOrThrow("a.ch-name")
					val url = a.attrAsRelativeUrl("href")
					val name = a.text()
					val dateText = li.selectFirst("span")?.text()
					val date = try {
						if (!dateText.isNullOrEmpty()) {
							SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(dateText)?.time ?: 0L
						} else 0L
					} catch (e: Exception) {
						0L
					}
					MangaChapter(
						id = generateUid(url),
						title = name,
						number = elements.size - i.toFloat(),
						volume = 0,
						url = url,
						scanlator = null,
						uploadDate = date,
						branch = null,
						source = source,
					)
				}
			}.reversed(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/full"
		val doc = webClient.httpGet(fullUrl).parseHtml()

		return doc.select("div.chapter-container img").mapNotNull { img ->
			val url = img.attrAsAbsoluteUrlOrNull("src")
			url?.let {
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
	}
}
