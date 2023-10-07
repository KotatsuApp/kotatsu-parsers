package org.koitharu.kotatsu.parsers.site.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TRUYENQQ", "Truyenqq", "vi")
internal class Truyenqq(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.TRUYENQQ, 42) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST)
	override val configKeyDomain = ConfigKey.Domain("truyenqqvn.com")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tagQuery = if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = ",") { it.key }
		val url = if (!query.isNullOrEmpty()) {
			buildString {
				append("https://$domain")
				append("/tim-kiem/trang-$page.html")
				append("?q=")
				append(query.urlEncoded())
			}
		} else {
			buildString {
				append("https://$domain")
				append("/tim-kiem-nang-cao/trang-$page.html")
				append("?status=-1&country=0&sort=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("4")
					SortOrder.UPDATED -> append("2")
					SortOrder.NEWEST -> append("0")
					else -> append("2")
				}
				append("&category=")
				append(tagQuery)
				append("&notcategory=&minchapter=0")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.requireElementById("main_homepage").select("li").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirstOrThrow(".book_name").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = li.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem-nang-cao.html").parseHtml()
		return doc.select(".advsearch-form div.genre-item").mapNotNullToSet {
			MangaTag(
				key = it.selectFirstOrThrow("span").attr("data-id"),
				title = it.text(),
				source = source,
			)
		}
	}


	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
		return manga.copy(
			altTitle = doc.selectFirst("h2.other-name")?.text(),
			tags = doc.select("ul.list01 li").mapNotNullToSet {
				val key = it.attr("href").substringAfterLast("-").substringBeforeLast(".")
				MangaTag(
					key = key,
					title = it.text(),
					source = source,
				)
			},
			state = when (doc.selectFirstOrThrow(".status p.col-xs-9").text()) {
				"Đang Cập Nhật" -> MangaState.ONGOING
				"Hoàn Thành" -> MangaState.FINISHED
				else -> null
			},
			author = doc.selectFirst("li.author a")?.text(),
			description = doc.selectFirst(".story-detail-info")?.html(),
			chapters = doc.select("div.list_chapter div.works-chapter-item").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val name = a.text()
				val dateText = div.selectFirstOrThrow(".time-chap").text()
				MangaChapter(
					id = generateUid(href),
					name = name,
					number = i + 1,
					url = href,
					scanlator = null,
					uploadDate = dateFormat.tryParse(dateText),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirstOrThrow(".chapter_content")
		return root.select("div.page-chapter").map { div ->
			val img = div.selectFirstOrThrow("img")
			val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
