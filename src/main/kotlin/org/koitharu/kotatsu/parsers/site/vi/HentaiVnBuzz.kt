package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENTAIVNBUZZ", "HentaiVn.buzz", "vi", type = ContentType.HENTAI)
internal class HentaiVnBuzz(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.HENTAIVNBUZZ, 24) {

	override val configKeyDomain = ConfigKey.Domain("truyentranh3q.com")

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.NEWEST,
			SortOrder.POPULARITY,
			SortOrder.RATING,
		)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.OTHER,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem-nang-cao")

			append("?")

			if (page > 1) {
				append("page=")
				append(page)
				append("&")
			}

			if (!filter.query.isNullOrEmpty()) {
				append("keyword=")
				append(filter.query.urlEncoded())
				append("&")
			}

			append("sort=")
			append(
				when (order) {
					SortOrder.UPDATED -> "0"
					SortOrder.NEWEST -> "1"
					SortOrder.POPULARITY -> "2"
					SortOrder.RATING -> "6"
					else -> "0"
				},
			)

			append("&status=")
			append(
				when (filter.states.oneOrThrowIfMany()) {
					MangaState.ONGOING -> "1"
					MangaState.FINISHED -> "2"
					else -> "0"
				},
			)

			append("&country=")
			append(
				when (filter.types.oneOrThrowIfMany()) {
					ContentType.MANGA -> "manga"
					ContentType.MANHWA -> "manhwa"
					ContentType.MANHUA -> "manhua"
					ContentType.OTHER -> "other"
					else -> "all"
				},
			)

			if (filter.tags.isNotEmpty()) {
				append("&categories=")
				append(filter.tags.joinToString(",") { it.key })
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("ul.list_grid.grid > li").map { element ->
			val aTag = element.selectFirstOrThrow("h3 a")
			val tags = element.select(".genre-item").mapToSet {
				MangaTag(
					key = it.attr("href").substringAfterLast('-').substringBeforeLast('.'),
					title = it.text().toTitleCase(sourceLocale),
					source = source,
				)
			}

			val href = aTag.attrAsRelativeUrl("href")

			Manga(
				id = generateUid(href),
				title = aTag.text(),
				altTitles = emptySet(),
				url = href,
				publicUrl = aTag.attrAsAbsoluteUrl("href"),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = element.selectFirst(".book_avatar a img")?.src(),
				tags = tags,
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tags = doc.select("ul.list01 li").mapToSet {
			MangaTag(
				key = it.attr("href").substringAfterLast('-').substringBeforeLast('.'),
				title = it.text().toTitleCase(sourceLocale),
				source = source,
			)
		}
		val author = doc.selectFirst("li.author a")?.textOrNull()

		return manga.copy(
			altTitles = setOfNotNull(doc.selectFirst("h2.other-name")?.textOrNull()),
			authors = setOfNotNull(author),
			tags = tags,
			description = doc.selectFirst("div.story-detail-info")?.html(),
			state = when (doc.selectFirst(".status p.col-xs-9")?.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Hoàn thành" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select("div.list_chapter div.works-chapter-item").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val name = a.text()
				MangaChapter(
					id = generateUid(href),
					title = name,
					number = i + 1f,
					volume = 0,
					url = href,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".chapter_content img").map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem-nang-cao").parseHtml()
		val elements = doc.select(".genre-item")
		return elements.mapIndexed { i, element ->
			MangaTag(
				key = (i + 1).toString(),
				title = element.text().toTitleCase(sourceLocale),
				source = source,
			)
		}.toSet()
	}
}
