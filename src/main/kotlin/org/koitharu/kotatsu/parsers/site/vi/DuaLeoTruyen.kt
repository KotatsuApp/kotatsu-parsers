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

@MangaSourceParser("DUALEOTRUYEN", "DuaLeoTruyen", "vi", type = ContentType.HENTAI)
internal class DuaLeoTruyen(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.DUALEOTRUYEN, 60) {

  	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("dualeotruyenomega.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/tim-kiem.html")
					append("?key=")
					append(filter.query.urlEncoded())
				}
				filter.tags.isNotEmpty() -> {
					append("/the-loai/")
					append(filter.tags.first().key)
					append(".html")
				}
				else -> when (order) {
					SortOrder.POPULARITY -> append("/top-ngay.html")
					else -> append("/truyen-moi-cap-nhat.html")
				}
			}
			if (page > 1) {
				append("?page=")
				append(page)
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".box_list > .li_truyen").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirst(".name")?.text().orEmpty(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = li.selectFirst("img")?.absUrl("data-src").orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

		return manga.copy(
			altTitle = doc.selectFirst(".box_info_right h2")?.textOrNull(),
			tags = doc.select("ul.list-tag-story li a").mapToSet {
				MangaTag(
					key = it.attr("href").substringAfterLast('/').substringBefore('.'),
					title = it.text().toTitleCase(sourceLocale),
					source = source
				)
			},
			state = when (doc.selectFirst(".info-item:has(.fa-rss)")?.text()?.removePrefix("Tình trang: ")) {
				"Đang cập nhật" -> MangaState.ONGOING
				"Full" -> MangaState.FINISHED
				else -> null
			},
			author = doc.selectFirst(".info-item:has(.fa-user)")?.textOrNull()?.removePrefix("Tác giả: "),
			description = doc.selectFirst(".story-detail-info")?.html(),
			chapters = doc.select(".list-chapters .chapter-item").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow(".chap_name a")
				val href = a.attrAsRelativeUrl("href")
				val dateText = div.selectFirst(".chap_update")?.text()
				MangaChapter(
					id = generateUid(href),
					name = a.text(),
					number = i + 1f,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(dateText),
					branch = null,
					source = source,
					volume = 0,
				)
			}
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chapterId = doc.selectFirst("input[name=chap]")?.`val`()
		val comicsId = doc.selectFirst("input[name=truyen]")?.`val`()
		if (chapterId != null && comicsId != null) {
			webClient.httpPost(
				url = "https://$domain/process.php",
				form = mapOf(
					"action" to "update_view_chap",
					"truyen" to comicsId,
					"chap" to chapterId
				)
			)
		}

		return doc.select(".content_view_chap img").mapIndexed { i, img ->
			val url = img.absUrl("data-original")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return listOf(
			"18+", "Đam Mỹ", "Harem", "Truyện Màu", "BoyLove", "GirlLove",
			"Phiêu lưu", "Yaoi", "Hài Hước", "Bách Hợp", "Chuyển Sinh", "Drama",
			"Hành Động", "Kịch Tính", "Cổ Đại", "Ecchi", "Hentai", "Lãng Mạn",
			"Người Thú", "Tình Cảm", "Yuri", "Oneshot", "Doujinshi", "ABO"
		).mapToSet { name ->
			MangaTag(
				key = name.lowercase().replace(' ', '-'),
				title = name,
				source = source
			)
		}
	}
}
