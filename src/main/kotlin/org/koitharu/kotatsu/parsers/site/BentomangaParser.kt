package org.koitharu.kotatsu.parsers.site

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import java.util.*

@MangaSourceParser("BENTOMANGA", "Bentomanga", "fr")
internal class BentomangaParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.BENTOMANGA, 10) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	override val configKeyDomain = ConfigKey.Domain("www.bentomanga.com", null)

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/113.0")
		.build()

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = urlBuilder()
			.addPathSegment("manga_list")
			.addQueryParameter("limit", page.toString())
			.addQueryParameter(
				"order_by",
				when (sortOrder) {
					SortOrder.UPDATED -> "update"
					SortOrder.POPULARITY -> "views"
					SortOrder.RATING -> "top"
					SortOrder.NEWEST -> "create"
					SortOrder.ALPHABETICAL -> "name"
				},
			)
		if (!tags.isNullOrEmpty()) {
			url.addQueryParameter("withCategories", tags.joinToString(",") { it.key })
		}
		if (!query.isNullOrEmpty()) {
			url.addQueryParameter("search", query)
		}
		val root = webClient.httpGet(url.build()).parseHtml().requireElementById("mangas_content")
		return root.select(".manga[data-manga]").map { div ->
			val header = div.selectFirstOrThrow(".manga_header")
			val href = header.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirstOrThrow("h1").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = div.getElementsByAttributeValue("data-icon", "avg_rate")
					.firstOrNull()
					?.textOrNull()
					?.toFloatOrNull()
					?.div(10f)
					?: RATING_UNKNOWN,
				isNsfw = div.selectFirst(".badge-adult_content") != null,
				coverUrl = div.selectFirstOrThrow("img").src(),
				tags = div.selectFirst(".component-manga-categories")
					.assertNotNull("tags")
					?.select("a")
					?.mapToSet { a ->
						MangaTag(
							title = a.text().toTitleCase(sourceLocale),
							key = a.attr("href").substringAfterLast('='),
							source = source,
						)
					}.orEmpty(),
				state = null,
				author = null,
				description = div.selectFirst(".manga_synopsis")?.html().assertNotNull("description"),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val root = webClient.httpGet(mangaUrl).parseHtml()
			.requireElementById("container_manga_show")
		return manga.copy(
			altTitle = root.selectFirst(".component-manga-title_alt")?.textOrNull().assertNotNull("altTitle"),
			description = root.selectFirst(".datas_synopsis")?.html().assertNotNull("description")
				?: manga.description,
			state = when (root.selectFirst(".datas_more-status-data")?.textOrNull().assertNotNull("status")) {
				"En cours" -> MangaState.ONGOING
				else -> null
			},
			author = root.selectFirst(".datas_more-authors-people")?.textOrNull().assertNotNull("author"),
			chapters = run {
				val input = root.selectFirst("input[name=\"limit\"]") ?: return@run parseChapters(root)
				val max = input.attr("max").toInt()
				if (max <= 1) {
					parseChapters(root)
				} else {
					coroutineScope {
						val result = ArrayList<MangaChapter>(parseChapters(root))
						result.ensureCapacity(result.size * max)
						(2..max).map { i ->
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

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapterUrl).parseHtml()
		val chapterId = doc.head().getElementsByAttribute("data-chapter-id").first()!!.attr("data-chapter-id")
		val json = webClient.httpGet(
			"https://$domain/api/?id=$chapterId&type=chapter",
			Headers.headersOf(
				"Referer", chapterUrl,
				"x-requested-with", "XMLHttpRequest",
			),
		).parseJson()
		if (json.getIntOrDefault("type", 1) == 2) {
			throw ParseException("Light Novels are not supported", chapterUrl)
		}
		val baseUrl = json.getString("baseImagesUrl")
		val pages = json.getJSONArray("page_array")
		return (0 until pages.length()).map { i ->
			val url = concatUrl(baseUrl, pages.getString(i))
			MangaPage(
				id = generateUid(url),
				url = url.toAbsoluteUrl(domain),
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val root = webClient.httpGet(urlBuilder().addPathSegment("manga_list").build())
			.parseHtml()
			.requireElementById("search_options-form")
		return root.getElementsByAttributeValue("name", "categories[]")
			.mapToSet { input ->
				val div = input.parents().first()!!
				MangaTag(
					title = div.text().toTitleCase(sourceLocale),
					key = input.attr("value"),
					source = source,
				)
			}
	}

	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?limit=$page").parseHtml().body())
	}

	private fun parseChapters(root: Element): List<MangaChapter> {
		return root.requireElementById("chapters_content")
			.select(".component-chapter").map { div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val title = div.selectFirstOrThrow(".chapter_volume").text()
				val name = div.selectFirst(".chapter_title")?.textOrNull()
				MangaChapter(
					id = generateUid(href),
					name = if (name != null && name != title) "$title: $name" else title,
					number = href.substringAfterLast('/').toIntOrNull() ?: 0,
					url = href,
					scanlator = div.selectFirst(".team_link-name")?.textOrNull(),
					uploadDate = div.selectFirst(".component-chapter-date")
						?.ownTextOrNull()
						.parseDate(),
					branch = null,
					source = source,
				)
			}
	}

	private fun String?.parseDate(): Long {
		if (this == null) {
			assert(false) { "Date is null" }
			return 0L
		}
		val parts = split(' ')
		assert(parts.size == 2) { "Wrong date $this" }
		val count = parts.getOrNull(0)?.toIntOrNull() ?: return 0L
		val unit = parts.getOrNull(1) ?: return 0L
		val calendarUnit = when (unit) {
			"s" -> Calendar.SECOND
			"min" -> Calendar.MINUTE
			"h" -> Calendar.HOUR
			"j" -> Calendar.DAY_OF_YEAR
			"sem." -> Calendar.WEEK_OF_YEAR
			"mois" -> Calendar.MONTH
			"ans", "an" -> Calendar.YEAR
			else -> {
				assert(false) { "Unknown time unit $unit" }
				return 0L
			}
		}
		val calendar = Calendar.getInstance()
		calendar.add(calendarUnit, -count)
		return calendar.timeInMillis
	}

	private fun Element.src(): String {
		return attrAsAbsoluteUrlOrNull("data-cfsrc")
			?: attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-src")
			?: run {
				assert(false) { "Image src not found" }
				""
			}
	}
}
