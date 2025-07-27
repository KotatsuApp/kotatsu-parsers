package org.koitharu.kotatsu.parsers.site.gallery

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.AbstractMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class GalleryParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
) : AbstractMangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.NEWEST, SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder().apply {
			when {
				!filter.query.isNullOrEmpty() -> addQueryParameter("search", filter.query)
				filter.tags.isNotEmpty() -> addPathSegments(filter.tags.first().key)
				order == SortOrder.POPULARITY -> addPathSegment("hot")
			}

			addQueryParameter("start", offset.toString())
		}.build()

		val content = webClient.httpGet(url).parseHtml()
		val currentPage = content.selectFirst("a.pagination-link.is-current")?.text()?.toIntOrNull()
		val titlePage = content.selectFirst("head > title")?.text()
			?.substringAfter("page ", "")
			?.substringBefore(" ", "")
			?.toIntOrNull()

		if (titlePage != null && currentPage != titlePage) return emptyList()

		return content.select("div.items-row").map { el ->
			val titleEl = el.selectFirstOrThrow("div.page-header a.item-link")
			val relUrl = titleEl.attrOrThrow("href")
			Manga(
				id = generateUid(relUrl),
				url = relUrl,
				title = titleEl.text(),
				altTitles = emptySet(),
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
				authors = emptySet(),
				largeCoverUrl = null,
				description = null,
				chapters = null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val content = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val description = content.selectFirst("div.article-info")?.text()?.trim() ?: ""
		val df = SimpleDateFormat("HH:mm dd-MM-yyyy")
		val time = content.selectFirst("div.article-info > small")?.text()?.trim()
		val chapters = content.selectFirst("nav.pagination")?.select("a.pagination-link")
			?.mapChapters { index, element ->
				val relUrl = element.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(relUrl),
					title = null,
					number = index + 1f,
					volume = 0,
					url = relUrl,
					scanlator = null,
					uploadDate = df.parseSafe(time),
					branch = null,
					source = source,
				)
			}.orEmpty()
		return manga.copy(chapters = chapters, description = description)
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

	protected suspend fun fetchTags(): Set<MangaTag> {
		val root = webClient.httpGet("https://$domain").parseHtml()
		return root.select("div#navbar-main a.navbar-item").map { a ->
			MangaTag(
				title = a.text(),
				key = a.attr("href").removePrefix("/"),
				source = source,
			)
		}.toSet()
	}
}
