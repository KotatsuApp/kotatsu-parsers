package org.koitharu.kotatsu.parsers.site.vi

import androidx.collection.ArrayMap
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.getOrNull
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("BLOGTRUYENVN", "BlogTruyenVN", "vi")
internal class BlogTruyenVN(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.BLOGTRUYENVN, pageSize = 20) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("blogtruyenvn.org", "blogtruyenvn.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = cacheTags.get().values.toSet(),
	)

	private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
	private var cacheTags = suspendLazy(initializer = ::fetchTags)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return when {
			!filter.query.isNullOrEmpty() -> {
				val searchUrl = "https://${domain}/timkiem/nangcao/1/0/-1/-1?txt=${filter.query.urlEncoded()}&p=$page"
				val searchContent = webClient.httpGet(searchUrl).parseHtml()
					.selectFirst("section.list-manga-bycate > div.list")
				parseMangaList(searchContent)
			}

			else -> {

				if (filter.tags.isNotEmpty()) {
					filter.tags.oneOrThrowIfMany().let {
						val categoryAjax =
							"https://${domain}/ajax/Category/AjaxLoadMangaByCategory?id=${it?.key}&orderBy=5&p=$page"
						val listContent = webClient.httpGet(categoryAjax).parseHtml().selectFirst("div.list")
						parseMangaList(listContent)
					}
				} else {
					getNormalList(page)
				}
			}
		}
	}

	private suspend fun getNormalList(page: Int): List<Manga> {
		val pageLink = "https://${domain}/page-$page"
		val doc = webClient.httpGet(pageLink).parseHtml()
		val listElements = doc.selectFirstOrThrow("section.list-mainpage.listview")
			.select("div.bg-white.storyitem")

		return listElements.mapNotNull { el ->
			val linkTag = el.selectFirst("div.fl-l > a") ?: return@mapNotNull null
			val relativeUrl = linkTag.attrAsRelativeUrl("href")
			val tags = cacheTags.getOrNull()?.let { tagMap ->
				el.select("footer > div.category > a").mapNotNullToSet { a ->
					tagMap[a.text()]
				}
			}

			Manga(
				id = generateUid(relativeUrl),
				title = linkTag.attr("title"),
				altTitle = null,
				description = el.selectFirst("p.al-j.break.line-height-15")?.text(),
				url = relativeUrl,
				publicUrl = relativeUrl.toAbsoluteUrl(domain),
				coverUrl = linkTag.selectLast("img")?.src().orEmpty(),
				source = source,
				tags = tags ?: emptySet(),
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
				coverUrl = mangaInfo.selectFirst("div > img.img")?.src().orEmpty(),
				isNsfw = false,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Map<String, MangaTag> {
		val doc = webClient.httpGet("/timkiem/nangcao".toAbsoluteUrl(domain)).parseHtml()
		val tagItems = doc.select("li[data-id]")
		val tagMap = ArrayMap<String, MangaTag>(tagItems.size)
		for (tag in tagItems) {
			val title = tag.text()
			tagMap[title] = MangaTag(
				title = title,
				key = tag.attr("data-id"),
				source = source,
			)
		}
		return tagMap
	}

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

		val tags = cacheTags.getOrNull()?.let { tagMap ->
			descriptionElement.select("p > span.category").mapNotNullToSet {
				val tagName = it.selectFirst("a")?.textOrNull() ?: return@mapNotNullToSet null
				tagMap[tagName]
			}
		}

		return manga.copy(
			tags = tags ?: emptySet(),
			author = descriptionElement.selectFirst("p:contains(Tác giả) > a")?.text(),
			description = doc.selectFirst(".detail .content")?.html(),
			chapters = parseChapterList(doc),
			largeCoverUrl = doc.selectLast("div.thumbnail > img")?.src().orEmpty(),
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
				number = index + 1f,
				volume = 0,
				url = relativeUrl,
				scanlator = null,
				uploadDate = uploadDate,
				branch = null,
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
					url = img.src().orEmpty(),
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
}
