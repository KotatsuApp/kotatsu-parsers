package org.koitharu.kotatsu.parsers.site.manga18

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class Manga18Parser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}


	@JvmField
	protected val ongoing: Set<String> = setOf(
		"On Going",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Completed",
	)

	protected open val listUrl = "list-manga/"
	protected open val tagUrl = "manga-list/"
	protected open val datePattern = "dd-MM-yyyy"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')

			if (filter.tags.isNotEmpty() && filter.query != null) {
				throw IllegalArgumentException("Search is not supported with tags")
			}

			if (filter.tags.isNotEmpty()) {
				filter.tags.oneOrThrowIfMany()?.let {
					append(tagUrl)
					append(it.key)
					append('/')
					append(page.toString())
				}
			}

			if (!filter.query.isNullOrEmpty()) {
				append(listUrl)
				append(page.toString())
				append("?search=")
				append(filter.query.urlEncoded())
				append("&order_by=latest")
			}

			append("?order_by=")
			when (order) {
				SortOrder.POPULARITY -> append("views")
				SortOrder.UPDATED -> append("lastest")
				SortOrder.ALPHABETICAL -> append("name")
				else -> append("latest")
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.story_item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("div.mg_info")?.selectFirst("div.mg_name a")?.text().orEmpty(),
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
		val doc = webClient.httpGet("https://$domain/$listUrl/").parseHtml()
		return doc.select("div.grid_cate li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix('/').substringAfterLast('/')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.detail_reviewContent"
	protected open val selectState = "div.item:contains(Status) div.info_value"
	protected open val selectAlt = "div.item:contains(Other name) div.info_value"
	protected open val selectTag = "div.item:contains(Categories) div.info_value a"
	protected open val selectAuthor =
		"div.info_label:contains(author) + div.info_value, div.info_label:contains(autor) + div.info_value"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body().selectFirstOrThrow("div.detail_listInfo")
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirst(selectDesc)?.html()
		val stateDiv = body.selectFirst(selectState)
		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val alt = body.selectFirst(selectAlt)?.text().takeIf { it != "Updating" || it.isNotEmpty() }
		val author = body.selectFirst(selectAuthor)?.text().takeIf { it != "Updating" }
		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc.orEmpty(),
			altTitle = alt,
			author = author,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}


	protected open val selectDate = "div.item p"
	protected open val selectChapter = "div.chapter_box li"

	protected open suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = dateFormat.tryParse(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(slides_p_path)")
		val urlEncoded = script.data().substringAfter('[').substringBefore(",]").replace("\"", "").split(",")
		return urlEncoded.map { url ->
			val img = context.decodeBase64(url).toString(Charsets.UTF_8)
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}
}
