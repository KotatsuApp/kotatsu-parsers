package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*
import org.koitharu.kotatsu.parsers.Broken

@Broken
@MangaSourceParser("BLOGTRUYEN", "BlogTruyen", "vi")
internal class BlogTruyenParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.BLOGTRUYEN, pageSize = 20) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("blogtruyenmoi.com")

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
		availableTags = availableTags(),
	)

	private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return when {
			!filter.query.isNullOrEmpty() -> {
				val searchUrl = "https://${domain}/timkiem/nangcao/1/0/-1/-1?txt=${filter.query.urlEncoded()}&p=$page"
				val searchContent = webClient.httpGet(searchUrl).parseHtml()
					.selectFirst("section.list-manga-bycate > div.list")
				parseMangaList(searchContent)
			}

			filter.tags.isNotEmpty() -> {
				filter.tags.oneOrThrowIfMany()?.let { tag ->
					val categoryAjax =
						"https://${domain}/ajax/Category/AjaxLoadMangaByCategory?id=${tag.key}&orderBy=5&p=$page"
					val listContent = webClient.httpGet(categoryAjax).parseHtml().selectFirst("div.list")
					parseMangaList(listContent)
				} ?: emptyList()
			}

			else -> {
				val url = "https://${domain}/ajax/Category/AjaxLoadMangaByCategory?id=0&orderBy=5&p=$page"
				val listContent = webClient.httpGet(url).parseHtml().selectFirst("div.list")
				parseMangaList(listContent)
			}
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

		val tagMap = availableTags().associateBy { it.title }
		val tags = descriptionElement.select("p > span.category").mapNotNullToSet {
			val tagName = it.selectFirst("a")?.textOrNull() ?: return@mapNotNullToSet null
			tagMap[tagName]
		}

		return manga.copy(
			tags = tags,
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

	// Check and archived by Draken
	private fun availableTags(): Set<MangaTag> = setOf(
		MangaTag("18+", "45", source),
		MangaTag("Action", "1", source),
		MangaTag("Adventure", "3", source),
		MangaTag("Comedy", "5", source),
		MangaTag("Comic", "6", source),
		MangaTag("Doujinshi", "7", source),
		MangaTag("Drama", "49", source),
		MangaTag("Ecchi", "48", source),
		MangaTag("Fantasy", "50", source),
		MangaTag("Full màu", "64", source),
		MangaTag("Game", "61", source),
		MangaTag("Gender bender", "51", source),
		MangaTag("Harem", "12", source),
		MangaTag("Historical", "13", source),
		MangaTag("Horror", "14", source),
		MangaTag("Isekai / Dị giới / Trọng sinh", "63", source),
		MangaTag("Josei", "15", source),
		MangaTag("Magic", "46", source),
		MangaTag("Manga", "55", source),
		MangaTag("Manhua", "17", source),
		MangaTag("Martial Arts", "19", source),
		MangaTag("Mecha", "21", source),
		MangaTag("Mystery", "22", source),
		MangaTag("Nấu ăn", "56", source),
		MangaTag("One shot", "23", source),
		MangaTag("Psychological", "24", source),
		MangaTag("Romance", "25", source),
		MangaTag("School Life", "26", source),
		MangaTag("Sci-fi", "27", source),
		MangaTag("Seinen", "28", source),
		MangaTag("Shoujo", "29", source),
		MangaTag("Shoujo Ai", "30", source),
		MangaTag("Shounen", "31", source),
		MangaTag("Shounen Ai", "32", source),
		MangaTag("Slice of life", "33", source),
		MangaTag("Smut", "34", source),
		MangaTag("Sports", "37", source),
		MangaTag("Supernatural", "38", source),
		MangaTag("Tạp chí truyện tranh", "39", source),
		MangaTag("Tragedy", "40", source),
		MangaTag("Trap (Crossdressing)", "58", source),
		MangaTag("VnComic", "42", source),
		MangaTag("Webtoon", "52", source),
		MangaTag("Yuri", "59", source),
		MangaTag("NTR", "62", source),
		MangaTag("Event BT", "60", source),
		MangaTag("Trinh thám", "57", source),
		MangaTag("Video Clip", "53", source),
		MangaTag("Truyện scan", "41", source),
		MangaTag("Soft Yaoi", "35", source),
		MangaTag("Soft Yuri", "36", source),
		MangaTag("Live action", "16", source),
		MangaTag("Tu chân - tu tiên", "66", source),
		MangaTag("Ngôn tình", "65", source),
	)
}
