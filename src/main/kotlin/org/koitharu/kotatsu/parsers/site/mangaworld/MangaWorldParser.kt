package org.koitharu.kotatsu.parsers.site.mangaworld

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

abstract class MangaWorldParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 16,
) : PagedMangaParser(context, source, pageSize) {
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.POPULARITY,
			SortOrder.ALPHABETICAL,
			SortOrder.NEWEST,
			SortOrder.ALPHABETICAL_DESC,
			SortOrder.UPDATED,
		)

	override val defaultSortOrder: SortOrder
		get() = SortOrder.ALPHABETICAL

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableStates: Set<MangaState> =
		EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED, MangaState.PAUSED)

	override val isMultipleTagsSupported = true

	override suspend fun getListPage(
		page: Int,
		filter: MangaListFilter?,
	): List<Manga> {
		val url =
			buildString {
				append("https://")
				append(domain)
				append("/archive?")
				when (filter) {
					is MangaListFilter.Search -> {
						append("keyword=")
						append(filter.query.urlEncoded())
					}

					is MangaListFilter.Advanced -> {
						if (filter.tags.isEmpty() && filter.states.isEmpty() && filter.sortOrder == SortOrder.UPDATED) return parseMangaList(
							webClient.httpGet("https://$domain/?page=$page").parseHtml(),
						)

						if (filter.tags.isNotEmpty()) {
							filter.tags.joinTo(this, "&") { it.key.substringAfter("archive?") }
						}

						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("&sort=most_read")
							SortOrder.ALPHABETICAL -> append("&sort=a-z")
							SortOrder.NEWEST -> append("&sort=newest")
							SortOrder.ALPHABETICAL_DESC -> append("&sort=z-a")
							else -> append("&sort=a-z")
						}
						when (filter.states.oneOrThrowIfMany()) {
							MangaState.ONGOING -> append("&status=ongoing")
							MangaState.FINISHED -> append("&status=completed")
							MangaState.ABANDONED -> append("&status=dropped")
							MangaState.PAUSED -> append("&status=paused")
							else -> Unit
						}
					}

					null -> Unit
				}
				append("&page=$page")
			}
		val doc = webClient.httpGet(url).parseHtml()
		return parseMangaList(doc)
	}

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(".comics-grid .entry").map { div ->
			val href = div.selectFirstOrThrow("a.thumb").attrAsRelativeUrl("href")
			val tags = div.select(".genres a[href*=/archive?genre=]")
				.mapNotNullToSet { MangaTag(it.ownText().toTitleCase(sourceLocale), it.attr("href"), source) }
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
				when (div.selectFirst(".status a")?.text()) {
					"In corso" -> MangaState.ONGOING
					"Finito" -> MangaState.FINISHED
					"Droppato" -> MangaState.ABANDONED
					"In pausa" -> MangaState.PAUSED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}


	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val genres = doc.select("div[aria-labelledby=genresDropdown] a").mapNotNullToSet {
			MangaTag(
				key = it.attr("href"),
				title = it.text().toTitleCase(sourceLocale),
				source = source,
			)
		}

		val types = doc.select("div[aria-labelledby=typesDropdown] a").mapNotNullToSet {
			MangaTag(
				key = it.attr("href"),
				title = it.text().toTitleCase(sourceLocale),
				source = source,
			)
		}

		return genres + types
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
					name = a.selectFirstOrThrow("span.d-inline-block").text(),
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
			val urlPage = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(urlPage),
				url = urlPage,
				preview = null,
				source = source,
			)
		}
	}
}
