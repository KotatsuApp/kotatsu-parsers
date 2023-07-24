package org.koitharu.kotatsu.parsers.site.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.SuspendLazy
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.insertCookies
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.selectLast
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toIntUp
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlBuilder
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet

private const val PAGE_SIZE = 15
private const val SEARCH_PAGE_SIZE = 10

@MangaSourceParser("HENTAIVN", "HentaiVN", "vi")
class HentaiVNParser(context: MangaLoaderContext) : MangaParser(context, MangaSource.HENTAIVN) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("hentaivn.autos", "hentaivn.tv")

	override val sortOrders: Set<SortOrder> = EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.POPULARITY,
			SortOrder.RATING,
			SortOrder.NEWEST
		)

	private val tagCache = SuspendLazy(this::fetchTags)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val chapterDeferred = async { fetchChapters(manga.url) }
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val infoEl = docs.selectFirstOrThrow("div.container")
			.selectFirstOrThrow("div.page-info")

		manga.copy(
			altTitle = infoEl.infoText("Tên Khác:"),
			author = infoEl.infoText("Tác giả"),
			description = infoEl.selectFirst("p:contains(Nội dung:)")
				?.nextElementSibling()
				?.outerHtml(),
			tags = tagCache.tryGet().getOrNull()?.let { tagMap ->
				infoEl.selectFirst("p:contains(Thể Loại:)")
					?.select("span > a")
					?.mapNotNullToSet {
						tagMap[it.text()]
					}
			}.orEmpty(),
			state = infoEl.infoText("Tình Trạng:")?.let {
				when (it) {
					"Đã hoàn thành" -> MangaState.FINISHED
					"Đang tiến hành" -> MangaState.ONGOING
					else -> null
				}
			},
			rating = docs.selectFirst("div.page_like")?.let {
				val like = it.selectFirst("div.but_like")?.text()?.trim()?.toIntOrNull() ?: return@let null
				val dislike = it.selectFirst("div.but_unlike")?.text()?.trim()?.toIntOrNull() ?: return@let null
				when {
					like == 0 && dislike == 0 -> RATING_UNKNOWN
					else -> like.toFloat() / (like + dislike)
				}
			} ?: RATING_UNKNOWN,
			chapters = chapterDeferred.await(),
		)
	}

	override suspend fun getList(offset: Int, query: String?, tags: Set<MangaTag>?, sortOrder: SortOrder): List<Manga> {
		val pageSize = if (tags.isNullOrEmpty()) PAGE_SIZE else SEARCH_PAGE_SIZE
		val page = (offset / pageSize.toFloat()).toIntUp() + 1
		return when {
			!tags.isNullOrEmpty() -> {
				val url = buildString {
					val tagKey = "tag[]".urlEncoded()
					append("/forum/search-plus.php?name=")
					append(query?.urlEncoded().orEmpty())
					append("&dou=&char=")
					tags.forEach { tag ->
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
			}

			!query.isNullOrEmpty() -> {
				urlBuilder()
				val searchUrl = "/tim-kiem-truyen.html?key=${query.urlEncoded()}&page=$page".toAbsoluteUrl(domain)
				val docs = webClient.httpGet(searchUrl).parseHtml()
				parseMainList(docs, page)
			}

			else -> {
				val site = if (sortOrder == SortOrder.UPDATED) "/chap-moi" else "/danh-sach"
				val url = "$site.html?page=$page".toAbsoluteUrl(domain)
				context.cookieJar.insertCookies(domain, *getSortCookies(sortOrder))
				val docs = webClient.httpGet(url).parseHtml()
				parseMainList(docs, page)
			}
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val ids = chapter.url.removePrefix("/").split('-').take(2)
		val mangaId = ids[0].toInt()
		val chapterId = ids[1].toInt()
		val contentUrl = "/list-loadchapter.php?id_episode=$chapterId&idchapshowz=$mangaId".toAbsoluteUrl(domain)
		val docs = webClient.httpGet(contentUrl).parseHtml()
		return docs.select("img").map {
			val pageUrl = it.attrAsAbsoluteUrl("src")
			MangaPage(
				id = generateUid(pageUrl),
				url = pageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return tagCache.get().values.toSet()
	}

	private suspend fun fetchTags(): Map<String, MangaTag> {
		val url = "/forum/search-plus.php".toAbsoluteUrl(domain)
		val docs = webClient.httpGet(url).parseHtml()
		return docs.selectFirstOrThrow("ul.ul-search")
			.select("li")
			.mapNotNull { el ->
				MangaTag(
					title = el.text(),
					key = el.selectFirst("input")?.attr("value") ?: return@mapNotNull null,
					source = source,
				)
			}.associateBy { it.title }
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
		val realPage = docs.selectFirst("ul.pagination > li > b")?.text()?.toIntOrNull() ?: 1
		if (page > realPage) {
			return emptyList()
		}

		return docs.selectFirstOrThrow("div.main")
			.selectFirstOrThrow("div.block-item")
			.select("ul > li.item")
			.map { el ->
				val relativeUrl = el.selectFirstOrThrow("div.box-cover > a").attrAsRelativeUrl("href")
				val descriptionsEl = el.selectFirstOrThrow("div.box-description")
				Manga(
					id = generateUid(relativeUrl),
					title = descriptionsEl.selectFirst("p > a")?.text().orEmpty(),
					altTitle = null,
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = el.selectFirst("div.box-cover img").imageUrl(),
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

		return docs.requireElementById("main")
			.selectFirstOrThrow("ul.search-ul")
			.select("li.search-li")
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
					coverUrl = el.selectFirst("div.search-img img")
						?.attrAsAbsoluteUrlOrNull("data-cfsrc")
						.orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	private suspend fun fetchChapters(mangaUrl: String): List<MangaChapter> {
		val slug = mangaUrl.substringAfterLast("/")
			.removeSuffix(".html")
		val name = slug.substringAfter("-")
		val id = slug.substringBefore("-").toInt()
		val chaptersAjax = "/list-showchapter.php?idchapshow=$id&idlinkanime=$name".toAbsoluteUrl(domain)
		val chaptersEl = webClient.httpGet(chaptersAjax).parseHtml()
		val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy")
		return chaptersEl.select("tbody > tr")
			.mapChapters(reversed = true) { index, element ->
				val titleEl = element.selectFirst("td > a") ?: return@mapChapters null
				val dateStr = element.selectLast("td")?.text()
				MangaChapter(
					id = generateUid(titleEl.attrAsRelativeUrl("href")),
					name = titleEl.text(),
					number = index + 1,
					url = titleEl.attrAsRelativeUrl("href"),
					scanlator = null,
					uploadDate = chapterDateFormat.tryParse(dateStr),
					branch = null,
					source = source,
				)
			}
	}

	private fun Element?.imageUrl(): String {
		if (this == null) {
			return ""
		}

		return attrAsRelativeUrlOrNull("data-src")
			?: attrAsRelativeUrlOrNull("data-srcset")
			?: attrAsRelativeUrlOrNull("src")
			?: ""
	}

	private fun Element.infoText(title: String) = selectFirst("span.info:contains($title)")
		?.parent()
		?.select("span:not(.info) > a")
		?.joinToString { it.text() }
}

