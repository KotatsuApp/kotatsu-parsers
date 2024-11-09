package org.koitharu.kotatsu.parsers.site.sinmh

import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class SinmhParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 36,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	@JvmField
	protected val ongoing: Set<String> = hashSetOf(
		"连载中",
	)

	@JvmField
	protected val finished: Set<String> = hashSetOf(
		"已完结",
	)

	protected open val searchUrl = "search/"
	protected open val listUrl = "list/"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			when {
				!filter.query.isNullOrEmpty() -> {
					append(searchUrl)
					append("?keywords=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page)
				}

				else -> {
					append(listUrl)
					filter.tags.oneOrThrowIfMany()?.let {
						append(it.key)
					}

					filter.states.oneOrThrowIfMany()?.let {
						append(
							when (it) {
								MangaState.ONGOING -> "-lianzai"
								MangaState.FINISHED -> "-wanjie"
								else -> ""
							},
						)
					}

					if (filter.tags.isNotEmpty() && filter.states.isNotEmpty()) {
						append('/')
					}

					when (order) {
						SortOrder.POPULARITY -> append("click/")
						SortOrder.UPDATED -> append("update/")
						else -> append('/')
					}
					append(page.toString())
					append('/')
				}
			}

		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("#contList > li, li.list-comic").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("p > a, h3 > a")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
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
		return doc.select(".filter-item:contains(按剧情) li a:not(.active)").mapToSet { a ->
			val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div#intro-all p"
	protected open val selectGenre = "ul.detail-list li:contains(漫画类型) a"
	protected open val selectState = "ul.detail-list li:contains(漫画状态) a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapters = getChapters(doc)
		val desc = doc.selectFirst(selectDesc)?.html()
		val state = doc.selectFirst(selectState)?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		manga.copy(
			tags = doc.body().select(selectGenre).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			state = state,
			chapters = chapters,
		)
	}


	protected open val selectChapter = "ul#chapter-list-1 li"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select(selectChapter).mapChapters { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = 0,
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	protected open val selectTestScript = "script:containsData(chapterImages = )"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {

		val host = webClient.httpGet("/js/config.js".toAbsoluteUrl(domain)).parseRaw().substringAfter("domain\":[\"")
			.substringBefore("\"]}").replace("http:", "https:")

		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow(selectTestScript).html()
		val images =
			script.substringAfter("chapterImages = [").substringBefore("];var chapterPath").replace("\"", "").split(",")
		val path = script.substringAfter("chapterPath = \"").substringBefore("\";var ")

		val pages = ArrayList<MangaPage>()
		images.map {
			val imageUrl = when {
				it.startsWith("https:\\/\\/") -> it.replace("\\", "")
				it.startsWith("http:\\/\\/") -> it.replace("\\", "").replace("http:", "https:")
				it.startsWith("\\/") -> host + it.replace("\\", "")
				it.startsWith("/") -> "$host$it"
				else -> "$host/$path$it"
			}
			pages.add(
				MangaPage(
					id = generateUid(imageUrl),
					url = imageUrl,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}

}
