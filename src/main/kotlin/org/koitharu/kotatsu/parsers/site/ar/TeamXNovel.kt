package org.koitharu.kotatsu.parsers.site.ar

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TEAMXNOVEL", "TeamXNovel", "ar")
internal class TeamXNovel(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.TEAMXNOVEL, 10) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)
	override val configKeyDomain = ConfigKey.Domain("team1x12.com")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()

		val url = buildString {
			append("https://$domain")
			if (!tags.isNullOrEmpty()) {
				append("/series?genre=")
				append(tag?.key.orEmpty())
				if (page > 1) {
					append("&page=")
					append(page)
				}
			} else if (!query.isNullOrEmpty()) {
				append("/series?search=")
				append(query.urlEncoded())
				if (page > 1) {
					append("&page=")
					append(page)
				}
			} else {
				when (sortOrder) {
					SortOrder.POPULARITY -> append("/series")
					SortOrder.UPDATED -> append("/")
					else -> append("/")
				}
				if (page > 1) {
					append("?page=")
					append(page)
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.listupd .bs .bsx").ifEmpty {
			doc.select("div.post-body .box")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(href),
				title = div.select(".tt, h3").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = div.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = when (div.selectFirst(".status")?.text()) {
					"مستمرة" -> MangaState.ONGOING
					"متوقف", "مكتمل" -> MangaState.FINISHED
					else -> null
				},
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/series").parseHtml()
		return doc.requireElementById("select_genre").select("option").mapNotNullToSet {
			MangaTag(
				key = it.attr("value"),
				title = it.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val mangaUrl = manga.url.toAbsoluteUrl(domain)

		val maxPageChapterSelect = doc.select(".pagination .page-item a")
		var maxPageChapter = 1
		if (!maxPageChapterSelect.isNullOrEmpty()) {
			maxPageChapterSelect.map {
				val i = it.attr("href").substringAfterLast("=").toInt()
				if (i > maxPageChapter) {
					maxPageChapter = i
				}
			}
		}

		return manga.copy(
			altTitle = null,
			state = when (doc.selectFirstOrThrow(".full-list-info:contains(الحالة:) a").text()) {
				"مستمرة" -> MangaState.ONGOING
				"متوقف", "مكتمل" -> MangaState.FINISHED
				else -> null
			},
			tags = doc.select(".review-author-info a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("="),
					title = a.text(),
					source = source,
				)
			},
			author = null,
			description = doc.selectFirstOrThrow(".review-content").text(),
			chapters = run {
				if (maxPageChapter == 1) {
					parseChapters(doc)
				} else {
					coroutineScope {
						val result = ArrayList(parseChapters(doc))
						result.ensureCapacity(result.size * maxPageChapter)
						(2..maxPageChapter).map { i ->
							async {
								loadChapters(mangaUrl, i)
							}
						}.awaitAll()
							.flattenTo(result)
						result
					}
				}
			}.reversed(),
		)
	}

	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?page=$page").parseHtml().body())
	}

	private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", sourceLocale)

	private fun parseChapters(root: Element): List<MangaChapter> {
		return root.requireElementById("chapter-contact").select(".eplister ul li")
			.map { li ->
				val url = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = li.selectFirstOrThrow(".epl-title").text(),
					number = url.substringAfterLast('/').toIntOrNull() ?: 0,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(li.selectFirstOrThrow(".epl-date").text()),
					branch = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(".image_list img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
