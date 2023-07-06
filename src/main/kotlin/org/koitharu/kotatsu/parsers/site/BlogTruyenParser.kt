package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.selectLast
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("BLOGTRUYEN", "BlogTruyen", "vi")
class BlogTruyenParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.BLOGTRUYEN, pageSize = 20) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("blogtruyen.vn")

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED)

	private val mutex = Mutex()
	private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
	private var cacheTags: ArrayMap<String, MangaTag>? = null

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val descriptionElement = doc.selectFirstOrThrow("div.description")
		val statusText = descriptionElement
			.selectFirst("p:contains(Trạng thái) > span.color-red")
			?.text()

		val state = when (statusText) {
			"Đang tiến hành" -> MangaState.ONGOING
			"Đã hoàn thành" -> MangaState.FINISHED
			else -> null
		}

		val rating = doc.selectFirst("span.total-vote")?.attr("ng-init")?.let { text ->
			val like = text.substringAfter("TotalLike=")
				.substringBefore(';')
				.toIntOrNull() ?: return@let RATING_UNKNOWN
			val dislike = text.substringAfter("TotalDisLike=")
				.toIntOrNull() ?: return@let RATING_UNKNOWN

			when {
				like == 0 && dislike == 0 -> RATING_UNKNOWN
				else -> like.toFloat() / (like + dislike)
			}
		}

		val tagMap = getOrCreateTagMap()
		val tags = descriptionElement.select("p > span.category").mapNotNullToSet {
			val tagName = it.selectFirst("a")?.text()?.trim() ?: return@mapNotNullToSet null
			tagMap[tagName]
		}

		return manga.copy(
			tags = tags,
			author = descriptionElement.selectFirst("p:contains(Tác giả) > a")?.text(),
			description = doc.selectFirst(".detail .content")?.html(),
			chapters = parseChapterList(doc),
			largeCoverUrl = doc.selectLast("div.thumbnail > img")?.imageUrl(),
			state = state,
			rating = rating ?: RATING_UNKNOWN,
			isNsfw = doc.getElementById("warningCategory") != null,
		)
	}

	private fun parseChapterList(doc: Document): List<MangaChapter> {
		val chapterList = doc.select("#list-chapters > p")
		return chapterList.mapChapters(reversed = true) { index, element ->
			val titleElement = element.selectFirst("span.title > a") ?: return@mapChapters null
			val name = titleElement.text()
			val relativeUrl = titleElement.attrAsRelativeUrl("href")
			val id = relativeUrl.substringAfter('/').substringBefore('/')
			val uploadDate = dateFormat.tryParse(element.select("span.publishedDate").text())
			MangaChapter(
				id = generateUid(id),
				name = name,
				number = index + 1,
				url = relativeUrl,
				scanlator = null,
				uploadDate = uploadDate,
				branch = null,
				source = source,
			)
		}
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		return when {
			!query.isNullOrEmpty() -> {
				val searchUrl = "https://${domain}/timkiem/nangcao/1/0/-1/-1?txt=${query.urlEncoded()}&p=$page"
				val searchContent = webClient.httpGet(searchUrl).parseHtml()
					.selectFirst("section.list-manga-bycate > div.list")
				parseMangaList(searchContent)
			}

			!tags.isNullOrEmpty() -> {
				val tag = tags.oneOrThrowIfMany()!!
				val categoryAjax =
					"https://${domain}/ajax/Category/AjaxLoadMangaByCategory?id=${tag.key}&orderBy=5&p=$page"
				val listContent = webClient.httpGet(categoryAjax).parseHtml().selectFirst("div.list")
				parseMangaList(listContent)
			}

			else -> getNormalList(page)
		}
	}

	private suspend fun getNormalList(page: Int): List<Manga> {
		val pageLink = "https://${domain}/page-$page"
		val doc = webClient.httpGet(pageLink).parseHtml()
		val listElements = doc.selectFirstOrThrow("section.list-mainpage.listview")
			.select("div.bg-white.storyitem")

		return listElements.mapNotNull {
			val linkTag = it.selectFirst("div.fl-l > a") ?: return@mapNotNull null
			val relativeUrl = linkTag.attrAsRelativeUrl("href")
			val tagMap = getOrCreateTagMap()
			val tags = it.select("footer > div.category > a").mapNotNullToSet { a ->
				tagMap[a.text()]
			}

			Manga(
				id = generateUid(relativeUrl),
				title = linkTag.attr("title"),
				altTitle = null,
				description = it.selectFirst("p.al-j.break.line-height-15")?.text(),
				url = relativeUrl,
				publicUrl = relativeUrl.toAbsoluteUrl(domain),
				coverUrl = linkTag.selectLast("img")?.imageUrl().orEmpty(),
				source = source,
				tags = tags,
				isNsfw = false,
				rating = RATING_UNKNOWN,
				author = null,
				state = null,
			)
		}
	}

	private fun parseMangaList(listElement: Element?): List<Manga> {
		listElement ?: return emptyList()

		return listElement.select("span.tiptip[data-tiptip]").mapNotNull {
			val mangaInfo = listElement.getElementById(it.attr("data-tiptip")) ?: return@mapNotNull null
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(relativeUrl),
				title = a.text(),
				altTitle = null,
				description = mangaInfo.select("div.al-j.fs-12").text(),
				url = relativeUrl,
				publicUrl = relativeUrl.toAbsoluteUrl(domain),
				coverUrl = mangaInfo.selectFirst("div > img.img")?.imageUrl().orEmpty(),
				isNsfw = false,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		fun generateImageId(index: Int) = generateUid("${chapter.url}/$index")

		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val pages = ArrayList<MangaPage>()
		doc.select("#content > img").forEach { img ->
			pages.add(
				MangaPage(
					id = generateImageId(pages.size),
					url = img.imageUrl(),
					preview = null,
					source = source,
				),
			)
		}

		// Some chapters use js script to render images
		val script = doc.selectLast("#content > script")
		if (script != null && script.data().contains("listImageCaption")) {
			val imagesStr = script.data().substringBefore(';').substringAfterLast('=').trim()
			val imageArr = JSONArray(imagesStr)
			for (i in 0 until imageArr.length()) {
				val imageUrl = imageArr.getJSONObject(i).getString("url")
				pages.add(
					MangaPage(
						id = generateImageId(pages.size),
						url = imageUrl,
						preview = null,
						source = source,
					),
				)
			}
		}

		return pages
	}

	override suspend fun getTags(): Set<MangaTag> {
		val map = getOrCreateTagMap()
		val tags = HashSet<MangaTag>(map.size)
		for (entry in map) {
			tags.add(entry.value)
		}

		return tags
	}


	private suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		cacheTags?.let { return@withLock it }
		val doc = webClient.httpGet("/timkiem/nangcao".toAbsoluteUrl(domain)).parseHtml()
		val tagItems = doc.select("li[data-id]")
		val tagMap = ArrayMap<String, MangaTag>(tagItems.size)
		for (tag in tagItems) {
			val title = tag.text().trim()
			tagMap[tag.text().trim()] = MangaTag(
				title = title,
				key = tag.attr("data-id"),
				source = source,
			)
		}

		cacheTags = tagMap
		tagMap
	}

	private fun Element.imageUrl(): String {
		return attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-cfsrc")
			?: ""
	}
}
