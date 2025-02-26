package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrOrNull
import org.koitharu.kotatsu.parsers.util.attrOrThrow
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlBuilder
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("BUONDUA", "Buon Dua", type = ContentType.OTHER)
internal class BuonDuaParser(context: MangaLoaderContext) : MangaParser(context, MangaParserSource.BUONDUA) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(
		"buondua.com",
		"buondua.us"
	)

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.NEWEST, SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getDetails(manga: Manga): Manga {
		val content = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val df = SimpleDateFormat("HH:mm dd-MM-yyyy")
		val time = content.selectFirst("div.article-info > small")?.text()?.trim()
		val chapters = content.selectFirst("nav.pagination")?.select("a.pagination-link")
			?.mapIndexed { index, element ->
				val relUrl = element.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(relUrl),
					name = "Chapter ${element.text()}",
					number = index + 1f,
					volume = 0,
					url = relUrl,
					scanlator = null,
					uploadDate = df.tryParse(time),
					branch = null,
					source = source,
				)
			}.orEmpty()
		return manga.copy(chapters = chapters)
	}

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder().apply {
			when {
				!filter.query.isNullOrEmpty() -> addQueryParameter("search", filter.query.urlEncoded())
				filter.tags.isNotEmpty() -> addPathSegments(filter.tags.first().key)
				order == SortOrder.POPULARITY -> addPathSegment("hot")
			}

			addQueryParameter("start", offset.toString())
		}.build()

		val content = webClient.httpGet(url).parseHtml()
		val currentPage = content.selectFirst("a.pagination-link.is-current")?.text()?.toIntOrNull()
		val titlePage = content.selectFirst("head > title")?.text()
			?.substringBefore("page ")
			?.substringAfter(" ")
			?.toIntOrNull()

		if (titlePage != null && currentPage != titlePage) return emptyList()

		return content.select("div.items-row").map { el ->
			val titleEl = el.selectFirstOrThrow("div.page-header a.item-link")
			val relUrl = titleEl.attrOrThrow("href")
			Manga(
				id = generateUid(relUrl),
				url = relUrl,
				title = titleEl.text(),
				altTitle = null,
				publicUrl = relUrl.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = el.selectFirst("div.item-thumb img")?.attr("src"),
				tags = el.select("div.item-tags > a.tag").mapNotNullToSet { tagEl ->
					MangaTag(
						title = tagEl.text(),
						key = tagEl.attrAsRelativeUrlOrNull("href")
							?.removePrefix("/") ?: return@mapNotNullToSet null,
						source = source,
					)
				},
				state = MangaState.FINISHED,
				author = null,
				largeCoverUrl = null,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val content = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return content.selectFirstOrThrow("div.article-fulltext").select("p > img").mapNotNull { el ->
			val url = el.attrOrNull("src") ?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
