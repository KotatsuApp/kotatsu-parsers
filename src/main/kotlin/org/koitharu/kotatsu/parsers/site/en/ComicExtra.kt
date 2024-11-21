package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("COMICEXTRA", "ComicExtra", "en", ContentType.COMICS)
internal class ComicExtra(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.COMICEXTRA, 25) {

	override val configKeyDomain = ConfigKey.Domain("comixextra.com")

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

					if (filter.tags.isNotEmpty() && filter.states.isEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
					} else if (filter.tags.isEmpty() && filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									MangaState.ONGOING -> "/ongoing-comic"
									MangaState.FINISHED -> "/completed-comic"
									else -> "/ongoing-comic"
								},
							)
						}

					}

					if (filter.tags.isNotEmpty() && filter.states.isNotEmpty()) {
						throw IllegalArgumentException(ErrorMessages.FILTER_BOTH_STATES_GENRES_NOT_SUPPORTED)
					} else {
						when (order) {
							SortOrder.POPULARITY -> append("popular-comic")
							SortOrder.UPDATED -> append("new-comic")
							SortOrder.NEWEST -> append("recent-comic")
							else -> append("new-comic")
						}
					}

					if (page > 1) {
						append("/")
						append(page.toString())
					}
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.movie-list-index div.cartoon-box").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirstOrThrow("h3").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
				tags = emptySet(),
				state = when (div.selectFirstOrThrow(".detail:contains(Stasus: )").text()) {
					"Stasus: Ongoing" -> MangaState.ONGOING
					"Stasus: Completed" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/popular-comic").parseHtml()
		return doc.select("li.tag-item a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast('/'),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("MM/dd/yy", sourceLocale)
		return manga.copy(
			altTitle = doc.selectFirstOrThrow("dt.movie-dt:contains(Alternate name:) + dd").text(),
			state = when (doc.selectFirstOrThrow("dt.movie-dt:contains(Status:) + dd a").text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			},
			tags = doc.select("dt.movie-dt:contains(Genres:) + dd a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			author = doc.select("dt.movie-dt:contains(Author:) + dd").text(),
			description = doc.getElementById("film-content")?.text(),
			chapters = doc.requireElementById("list").select("tr")
				.mapChapters(reversed = true) { i, tr ->
					val a = tr.selectFirstOrThrow("a")
					val url = a.attrAsRelativeUrl("href") + "/full"
					val name = a.text()
					val dateText = tr.select("td").last()?.text()
					MangaChapter(
						id = generateUid(url),
						name = name,
						number = i + 1f,
						volume = 0,
						url = url,
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
		return doc.select(".chapter-container img").map { img ->
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
