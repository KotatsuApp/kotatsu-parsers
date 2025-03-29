package org.koitharu.kotatsu.parsers.site.vi

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENTAIVNBUZZ", "HentaiVn.buzz", "vi", type = ContentType.HENTAI)
internal class HentaiVnBuzz(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.HENTAIVNBUZZ, 24) {

	override val configKeyDomain = ConfigKey.Domain("hentaivn.guru")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = when {
			!filter.query.isNullOrEmpty() -> {
				buildString {
					append("/tim-kiem?key_word=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page)
					}
				}
			}

			filter.tags.isNotEmpty() -> {
				val tag = filter.tags.first()
				buildString {
					append("/the-loai/")
					append(tag.key)
					append("?")
					when (order) {
						SortOrder.NEWEST -> append("sort=0")
						SortOrder.UPDATED -> append("sort=1")
						SortOrder.POPULARITY -> append("sort=2")
						else -> append("sort=0")
					}
					if (filter.states.isNotEmpty()) {
						filter.states.forEach {
							when (it) {
								MangaState.ONGOING -> append("&is_full=0")
								MangaState.FINISHED -> append("&is_full=1")
								else -> append("")
							}
						}
					}
					if (page > 1) {
						append("&page=")
						append(page)
					}
				}
			}

			else -> {
				buildString {
					append("/danh-sach/truyen-moi?")
					when (order) {
						SortOrder.NEWEST -> append("sort=0")
						SortOrder.UPDATED -> append("sort=1")
						SortOrder.POPULARITY -> append("sort=2")
						else -> append("sort=0")
					}
					if (filter.states.isNotEmpty()) {
						filter.states.forEach {
							when (it) {
								MangaState.ONGOING -> append("&is_full=0")
								MangaState.FINISHED -> append("&is_full=1")
								else -> append("")
							}
						}
					}
					if (page > 1) {
						append("&page=")
						append(page)
					}
				}
			}
		}

		val fullUrl = url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return when {
			!filter.query.isNullOrEmpty() -> parseSearchManga(doc)
			filter.tags.isNotEmpty() -> parseSearchManga(doc)
			else -> parseListManga(doc)
		}
	}

	private fun parseSearchManga(doc: Document): List<Manga> {
		return doc.select(".story-item-list.d-flex.align-items-center.position-relative.mb-1").map { div ->
			val href = div.selectFirstOrThrow("a.story-item-list__image").attrAsRelativeUrl("href")
			val coverUrl = div.selectFirst("img")?.attr("data-src")
			val title = div.selectFirst("img")?.attr("alt").orEmpty()
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	private fun parseListManga(doc: Document): List<Manga> {
		return doc.select(".story-item-list.d-flex.align-items-center.position-relative.mb-1").map { div ->
			val href = div.selectFirstOrThrow("a.story-item-list__image").attrAsRelativeUrl("href")
			val coverUrl = div.selectFirst("img")?.attr("data-src").orEmpty()
			val title = div.selectFirst("img")?.attr("alt").orEmpty()
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = if (isNsfwSource) ContentRating.ADULT else null,
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
		val author = doc.select("p:contains(Tác giả:) a").text().nullIfEmpty()
		return manga.copy(
			authors = setOfNotNull(author),
			tags = doc.select("div.mb-1 span a").mapToSet { element ->
				MangaTag(
					key = element.attr("href").substringAfter("/the-loai/"),
					title = element.text().substringBefore(',').trim(), // force trim before , symbol and space
					source = source,
				)
			},
			description = null,
			state = when (doc.select("p:contains(Trạng thái:) span").text()) {
				"Đang ra" -> MangaState.ONGOING
				"Hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select("div.story-detail__list-chapter--list ul.list-unstyled li a")
				.mapIndexed { i, element ->
					val href = element.attrAsRelativeUrl("href")
					val name = element.text().removePrefix("- ")
					MangaChapter(
						id = generateUid(href),
						title = name,
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val imageUrls = doc.select("meta[property='og:image']").map { it.attr("content") }
		val finalUrls = imageUrls.drop(1)
		return finalUrls.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val list = doc.select("ul.dropdown-menu.dropdown-menu-custom li a")
		return list.mapToSet { tags ->
			val href = tags.attr("href")
			val key = href.substringAfter("/the-loai/").substringBefore("/")
			val title = tags.text()
			MangaTag(
				key = key,
				title = title,
				source = source,
			)
		}
	}
}
