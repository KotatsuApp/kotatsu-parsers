package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 15
private const val SEARCH_PAGE_SIZE = 10

@MangaSourceParser("HENTAIVN", "HentaiVN", "vi", type = ContentType.HENTAI)
internal class HentaiVNParser(context: MangaLoaderContext) : MangaParser(context, MangaParserSource.HENTAIVN) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("hentaihvn.tv")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getOrCreateTagMap().values.toSet(),
	)

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return when {
			!filter.query.isNullOrEmpty() -> {
				val page = (offset / PAGE_SIZE.toFloat()).toIntUp() + 1
				urlBuilder()
				val searchUrl =
					"/tim-kiem-truyen.html?key=${filter.query.urlEncoded()}&page=$page".toAbsoluteUrl(domain)
				val docs = webClient.httpGet(searchUrl).parseHtml()
				parseMainList(docs, page)
			}

			else -> {
				val pageSize = if (filter.tags.isEmpty()) PAGE_SIZE else SEARCH_PAGE_SIZE
				val page = (offset / pageSize.toFloat()).toIntUp() + 1

				if (filter.tags.isNotEmpty()) {
					val url = buildString {
						val tagKey = "tag[]".urlEncoded()
						append("/forum/search-plus.php?name=")
						append("&dou=&char=")
						filter.tags.forEach { tag ->
							append("&")
							append(tagKey)
							append("=")
							append(tag.key)
						}
						append("&search=")
						append("&page=")
						append(page)
					}.toAbsoluteUrl(domain)

					val docs = webClient.httpGet(url).parseHtml()
					return parseAdvanceSearch(docs, page)
				} else {
					val site = if (order == SortOrder.UPDATED) "/chap-moi" else "/danh-sach"
					val url = "$site.html?page=$page".toAbsoluteUrl(domain)
					context.cookieJar.insertCookies(domain, *getSortCookies(order))
					val docs = webClient.httpGet(url).parseHtml()
					parseMainList(docs, page)
				}
			}
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val chapterDeferred = async { fetchChapters(manga.url) }
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val id = docs.location().substringAfterLast("/").substringBefore("-")
		val genreUrl = Regex(""""(list-info-theloai-mobile\.php?.+)"""").find(docs.toString())?.groupValues?.get(1)
		val genreDeferred = async {
			webClient.httpGet("https://$domain/$genreUrl").parseHtml().select("a.tag")
		}
		val infoElDeferred = async {
			webClient.httpGet("/list-info-all-mobile.php?id_anime=$id".toAbsoluteUrl(domain)).parseHtml()
		}
		val stateDocDeferred = async {
			webClient.httpGet("/list-info-time-mobile.php?id_anime=$id".toAbsoluteUrl(domain)).parseHtml()
		}
		val genre = genreDeferred.await()
		val tagMap = getOrCreateTagMap()
		val tags = genre.mapNotNullToSet { tagMap[it.text()] }
		val infoEl = infoElDeferred.await()
		val stateDoc = stateDocDeferred.await()
		manga.copy(
			altTitle = infoEl.selectFirst("span.info:contains(Tên Khác:)")?.parent()?.select("span:not(.info) > a")
				?.joinToString { it.text() },
			author = infoEl.select("p:contains(Tác giả:) a").text(),
			description = infoEl.select("p:contains(Nội dung:) + p").html(),
			tags = tags,
			state = stateDoc.select("p:contains(Tình Trạng:) a").firstOrNull()?.text()?.let {
				when (it) {
					"Đã hoàn thành" -> MangaState.FINISHED
					"Đang tiến hành" -> MangaState.ONGOING
					else -> null
				}
			},
			rating = docs.selectFirst("div.page_like")?.let {
				val like = it.selectFirst("div.but_like")?.text()?.toIntOrNull() ?: return@let null
				val dislike = it.selectFirst("div.but_unlike")?.text()?.toIntOrNull() ?: return@let null
				when {
					like == 0 && dislike == 0 -> RATING_UNKNOWN
					else -> like.toFloat() / (like + dislike)
				}
			} ?: RATING_UNKNOWN,
			chapters = chapterDeferred.await(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val docs = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return docs.select("#image > img").map {
			val pageUrl = it.requireSrc()
			MangaPage(
				id = generateUid(pageUrl),
				url = pageUrl,
				preview = null,
				source = source,
			)
		}
	}

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tags = webClient.httpGet("/forum/search-plus.php".toAbsoluteUrl(domain)).parseHtml()
			.selectFirstOrThrow("ul.ul-search").select("li")
		for (el in tags) {
			if (el.text().isEmpty()) continue
			tagMap[el.text()] = MangaTag(
				title = el.text(),
				key = el.selectFirst("input")?.attr("value") ?: continue,
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	private fun getSortCookies(sortOrder: SortOrder): Array<String> {
		return mapOf(
			SortOrder.POPULARITY to "view4",
			SortOrder.RATING to "view",
			SortOrder.NEWEST to "view0",
		).map { (order, cookieKey) ->
			val data = if (order == sortOrder) "1" else "0"
			"${cookieKey}=$data"
		}.toTypedArray()
	}

	private fun parseMainList(docs: Document, page: Int): List<Manga> {
		val realPage = docs.selectFirst(".pagination li b")?.text()?.toIntOrNull() ?: 1
		if (page > realPage) {
			return emptyList()
		}

		return docs.selectFirstOrThrow("div.main").selectFirstOrThrow("div.block-item").select("ul > li.item")
			.map { el ->
				val relativeUrl =
					el.selectFirstOrThrow("div.box-cover > a, div.box-cover-2 > a").attrAsRelativeUrl("href")
				val descriptionsEl = el.selectFirstOrThrow("div.box-description, div.box-description-2")
				Manga(
					id = generateUid(relativeUrl),
					title = descriptionsEl.selectFirst("a")?.text().orEmpty(),
					altTitle = null,
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = el.selectFirst("div.box-cover img, div.box-cover-2 img")?.src().orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	private fun parseAdvanceSearch(docs: Document, page: Int): List<Manga> {
		val realPage = docs.selectFirst("ul.pagination > li > b")?.text()?.toIntOrNull() ?: 1
		if (page > realPage) {
			return emptyList()
		}

		return docs.requireElementById("main").selectFirstOrThrow("ul.search-ul").select("li.search-li")
			.mapNotNull { el ->
				val titleEl = el.selectFirst("div.search-des > a") ?: return@mapNotNull null
				val relativeUrl = titleEl.attrAsRelativeUrl("href")
				Manga(
					id = generateUid(relativeUrl),
					title = titleEl.text(),
					altTitle = null,
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = el.selectFirst("div.search-img img")?.attrAsAbsoluteUrlOrNull("data-cfsrc").orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	private suspend fun fetchChapters(mangaUrl: String): List<MangaChapter> {
		val id = mangaUrl.substringAfterLast("/").substringBefore('-')
		val chaptersAjax = "/list-showchapter.php?idchapshow=$id".toAbsoluteUrl(domain)
		val chaptersEl = webClient.httpGet(chaptersAjax).parseHtml()
		val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy")
		return chaptersEl.select("tbody > tr").mapChapters(reversed = true) { index, element ->
			val titleEl = element.selectFirst("td > a") ?: return@mapChapters null
			val dateStr = element.selectLast("td")?.text()
			MangaChapter(
				id = generateUid(titleEl.attrAsRelativeUrl("href")),
				name = titleEl.text(),
				number = index + 1f,
				volume = 0,
				url = titleEl.attrAsRelativeUrl("href"),
				scanlator = null,
				uploadDate = chapterDateFormat.tryParse(dateStr),
				branch = null,
				source = source,
			)
		}
	}
}

