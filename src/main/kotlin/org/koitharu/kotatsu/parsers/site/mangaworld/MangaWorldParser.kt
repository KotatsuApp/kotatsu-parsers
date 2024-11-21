package org.koitharu.kotatsu.parsers.site.mangaworld

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MangaWorldParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 16,
) : PagedMangaParser(context, source, pageSize) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
	)

	override val defaultSortOrder: SortOrder
		get() = SortOrder.ALPHABETICAL

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED, MangaState.PAUSED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHUA,
			ContentType.MANHWA,
			ContentType.ONE_SHOT,
			ContentType.OTHER,
		),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {

		if (order == SortOrder.UPDATED) {
			if (filter.query != null || filter.tags.isNotEmpty() || filter.states.isNotEmpty() || filter.types.isNotEmpty() || filter.year != 0) {
				throw IllegalArgumentException("Sorting by update with filters is not supported by this source.")

			}
			return parseMangaList(webClient.httpGet("https://$domain/?page=$page").parseHtml())
		}

		val url =
			buildString {
				append("https://")
				append(domain)
				append("/archive?&page=")
				append(page.toString())

				filter.query?.let {
					append("&keyword=")
					append(filter.query.urlEncoded())
				}

				filter.tags.forEach {
					append("&genre=")
					append(it.key)
				}

				when (order) {
					SortOrder.POPULARITY -> append("&sort=most_read")
					SortOrder.POPULARITY_ASC -> append("&sort=less_read")
					SortOrder.ALPHABETICAL -> append("&sort=a-z")
					SortOrder.NEWEST -> append("&sort=newest")
					SortOrder.NEWEST_ASC -> append("&sort=oldest")
					SortOrder.ALPHABETICAL_DESC -> append("&sort=z-a")
					else -> append("&sort=a-z")
				}

				filter.states.forEach {
					when (it) {
						MangaState.ONGOING -> append("&status=ongoing")
						MangaState.FINISHED -> append("&status=completed")
						MangaState.ABANDONED -> append("&status=dropped")
						MangaState.PAUSED -> append("&status=paused")
						else -> {}
					}
				}

				filter.types.forEach {
					append("&type=")
					append(
						when (it) {
							ContentType.MANGA -> "manga"
							ContentType.MANHUA -> "manhua"
							ContentType.MANHWA -> "manhwa"
							ContentType.ONE_SHOT -> "oneshot"
							ContentType.OTHER -> "thai&type=vietnamese"
							else -> ""
						},
					)
				}

				if (filter.year != 0) {
					append("&year=")
					append(filter.year)
				}

				// author ( not query but same to tags )
				// filter.author.forEach {
				// 	append("&author=")
				// 	append(it.key)
				// }

				// artist ( not query but same to tags )
				// filter.artist.forEach {
				// 	append("&artist=")
				// 	append(it.key)
				// }

			}
		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(".comics-grid .entry").map { div ->
			val href = div.selectFirstOrThrow("a.thumb").attrAsRelativeUrl("href")
			val tags = div.select(".genres a[href*=/archive?genre=]")
				.mapToSet { MangaTag(it.ownText().toTitleCase(sourceLocale), it.attr("href"), source) }
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = div.selectFirst(".thumb img")?.attr("src").orEmpty(),
				title = div.selectFirst(".name a.manga-title")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = tags,
				author = div.selectFirst(".author a")?.text(),
				state =
					when (div.selectFirst(".status a")?.text()?.lowercase()) {
						"in corso" -> MangaState.ONGOING
						"finito" -> MangaState.FINISHED
						"droppato" -> MangaState.ABANDONED
						"in pausa" -> MangaState.PAUSED
						else -> null
					},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}


	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		return doc.select("div[aria-labelledby=genresDropdown] a").mapToSet {
			MangaTag(
				key = it.attr("href").substringAfterLast('='),
				title = it.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			altTitle =
				doc.selectFirst(".meta-data .font-weight-bold:contains(Titoli alternativi:)")
					?.parent()
					?.ownText()
					?.substringAfter(": ")
					?.trim(),
			description = doc.getElementById("noidungm")?.text().orEmpty(),
			chapters =
				doc.select(".chapters-wrapper .chapter a").mapChapters(reversed = true) { i, a ->
					val url = a.attrAsRelativeUrl("href").toAbsoluteUrl(domain)
					MangaChapter(
						id = generateUid(url),
						name = a.selectFirst("span.d-inline-block")?.text() ?: "Chapter : ${i + 1f}",
						number = i + 1f,
						volume = 0,
						url = "$url?style=list",
						scanlator = null,
						uploadDate =
							SimpleDateFormat("dd MMMM yyyy", Locale.ITALIAN).tryParse(
								a.selectFirst(".chap-date")?.text(),
							),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val selectWebtoonPages = "img.page-image"
		val selectMangaPages = "img.img-fluid"
		val imgSelector = if (doc.select(selectWebtoonPages).isNotEmpty()) selectWebtoonPages else selectMangaPages
		return doc.select(imgSelector).map { img ->
			val urlPage = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(urlPage),
				url = urlPage,
				preview = null,
				source = source,
			)
		}
	}
}
