package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("SAYHENTAI", "SayHentai", "vi", ContentType.HENTAI)
internal class SayHentai(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.SAYHENTAI, 20) {
	override val configKeyDomain = ConfigKey.Domain("sayhentai.one")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
			availableTags = fetchTags(),
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			if (!filter.query.isNullOrEmpty()) {
				append("/search?s=")
				append(filter.query.urlEncoded())
				append("&page=")
				append(page.toString())
			} else {
				if (filter.tags.isNotEmpty()) {
					append("/genre/")
					append(filter.tags.first().key)
					append("/")
				} else {
					append("/")
				}
				append("?page=")
				append(page.toString())
				val sortQuery = getSortOrderQuery(order, filter.tags.isNotEmpty())
				if (sortQuery.isNotEmpty()) {
					append("&")
					append(sortQuery)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".page-item-detail").mapNotNull { element ->
			val href = element.selectFirst(".item-summary a")?.attrAsRelativeUrl("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				title = element.selectFirst(".item-summary a")?.text().orEmpty(),
				coverUrl = element.selectFirst(".item-thumb img")?.src().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			altTitle = doc.selectFirst("h2.other-name")?.text(),
			author = doc.selectFirst("div.summary-heading:contains(Tác giả) + div.summary-content")?.text(),
			tags = doc.select("div.genres-content a[rel=tag]").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text().toTitleCase(sourceLocale),
					source = source,
				)
			},
			description = doc.selectFirst("div.summary__content")?.html(),
			state = when (doc.selectFirst("div.summary-heading:contains(Trạng thái) + div.summary-content")?.text()
				?.lowercase()) {
				"đang ra" -> MangaState.ONGOING
				"hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select("li.wp-manga-chapter").mapChapters(reversed = true) { i, element ->
				val a = element.selectFirst("a") ?: return@mapChapters null
				MangaChapter(
					id = generateUid(a.attrAsRelativeUrl("href")),
					name = a.text(),
					number = i + 1f,
					url = a.attrAsRelativeUrl("href"),
					uploadDate = parseChapterDate(element.selectFirst("span.chapter-release-date")?.text()),
					branch = null,
					scanlator = null,
					source = source,
					volume = 0,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.selectOrThrow("div.page-break img").mapIndexed { i, img ->
			val url = img.src().orEmpty()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(date: String?): Long {
		if (date == null) return 0
		return when {
			date.contains("giây trước") -> System.currentTimeMillis() - date.removeSuffix(" giây trước").toLong() * 1000
			date.contains("phút trước") -> System.currentTimeMillis() - date.removeSuffix(" phút trước")
				.toLong() * 60 * 1000

			date.contains("giờ trước") -> System.currentTimeMillis() - date.removeSuffix(" giờ trước")
				.toLong() * 60 * 60 * 1000

			date.contains("ngày trước") -> System.currentTimeMillis() - date.removeSuffix(" ngày trước")
				.toLong() * 24 * 60 * 60 * 1000

			date.contains("tuần trước") -> System.currentTimeMillis() - date.removeSuffix(" tuần trước")
				.toLong() * 7 * 24 * 60 * 60 * 1000

			date.contains("tháng trước") -> System.currentTimeMillis() - date.removeSuffix(" tháng trước")
				.toLong() * 30 * 24 * 60 * 60 * 1000

			date.contains("năm trước") -> System.currentTimeMillis() - date.removeSuffix(" năm trước")
				.toLong() * 365 * 24 * 60 * 60 * 1000

			else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(date)?.time ?: 0L
		}
	}

	private fun getSortOrderQuery(order: SortOrder, hasTags: Boolean): String {
		if (!hasTags) return ""
		return when (order) {
			SortOrder.UPDATED -> "m_orderby=latest"
			SortOrder.POPULARITY -> "m_orderby=views"
			SortOrder.ALPHABETICAL -> "m_orderby=alphabet"
			SortOrder.RATING -> "m_orderby=rating"
			else -> "m_orderby=latest"
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> = webClient.httpGet("https://$domain/genre").parseHtml()
		.select("ul.page-genres li a")
		.mapToSet { a ->
			val title = a.ownText().toTitleCase(sourceLocale)
			MangaTag(
				key = a.attr("href").substringAfterLast("/"),
				title = title,
				source = source,
			)
		}
}
