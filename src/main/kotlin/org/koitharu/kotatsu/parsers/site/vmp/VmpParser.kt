package org.koitharu.kotatsu.parsers.site.vmp

import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class VmpParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	protected open val listUrl = "xxx/"
	protected open val geneUrl = "genero/"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			when {

				!filter.query.isNullOrEmpty() -> {
					append(listUrl)
					append("/page/")
					append(page.toString())
					append("?s=")
					append(filter.query.urlEncoded())
				}

				else -> {

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append(geneUrl)
							append(it.key)
							append("/page/")
							append(page.toString())
						}
					} else {
						append(listUrl)
						append("/page/")
						append(page.toString())
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.blog-list-items div.entry").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("h2")?.text().orEmpty(),
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select("div.tagcloud a").mapToSet { a ->
			MangaTag(
				key = a.attr("href").removeSuffix("/").substringAfterLast(geneUrl, ""),
				title = a.text().toTitleCase(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		manga.copy(
			tags = doc.select("div.tax_box div.links ul:not(.post-categories) li a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast(geneUrl, ""),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = null,
			altTitle = null,
			author = null,
			state = null,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.wp-content img").map { div ->
			val img = div.selectFirstOrThrow("img")
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
