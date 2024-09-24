package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getIntOrDefault
import java.util.*

@Broken
@MangaSourceParser("BENTOMANGA", "BentoManga", "fr")
internal class BentomangaParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.BENTOMANGA, 10) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val configKeyDomain = ConfigKey.Domain("bentomanga.com", "www.bentomanga.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = urlBuilder()
			.host(domain)
			.addPathSegment("manga_list")
			.addQueryParameter("limit", page.toString())

		filter.query?.let {
			url.addQueryParameter("search", filter.query)
		}

		when (order) {
			SortOrder.UPDATED -> url.addQueryParameter("order_by", "update")
				.addQueryParameter("order", "desc")

			SortOrder.POPULARITY -> url.addQueryParameter("order_by", "views")
				.addQueryParameter("order", "desc")

			SortOrder.RATING -> url.addQueryParameter("order_by", "top")
				.addQueryParameter("order", "desc")

			SortOrder.NEWEST -> url.addQueryParameter("order_by", "create")
				.addQueryParameter("order", "desc")

			SortOrder.ALPHABETICAL -> url.addQueryParameter("order_by", "name")
				.addQueryParameter("order", "asc")

			SortOrder.ALPHABETICAL_DESC -> url.addQueryParameter("order_by", "name")
				.addQueryParameter("order", "desc")

			else -> url.addQueryParameter("order_by", "update")
				.addQueryParameter("order", "desc")
		}

		if (filter.tags.isNotEmpty()) {
			url.addQueryParameter("withCategories", filter.tags.joinToString(",") { it.key })
		}

		if (filter.tagsExclude.isNotEmpty()) {
			url.addQueryParameter("withoutCategories", filter.tagsExclude.joinToString(",") { it.key })
		}

		filter.states.oneOrThrowIfMany()?.let {
			url.addQueryParameter(
				"state",
				when (it) {
					MangaState.ONGOING -> "1"
					MangaState.FINISHED -> "2"
					MangaState.PAUSED -> "3"
					MangaState.ABANDONED -> "5"
					else -> "1"
				},
			)
		}
		val root = webClient.httpGet(url.build()).parseHtml().requireElementById("mangas_content")
		return root.select(".manga[data-manga]").map { div ->
			val header = div.selectFirstOrThrow(".manga_header")
			val href = header.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectFirst("h1")?.text().orEmpty(),
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
				coverUrl = div.selectFirst("img")?.src().assertNotNull("src").orEmpty(),
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
			altTitle = root.selectFirst(".component-manga-title_alt")?.text(),
			description = root.selectFirst(".datas_synopsis")?.html().assertNotNull("description")
				?: manga.description,
			state = when (root.selectFirst(".datas_more-status-data")?.textOrNull().assertNotNull("status")) {
				"En cours" -> MangaState.ONGOING
				"Terminé" -> MangaState.FINISHED
				"Abandonné" -> MangaState.ABANDONED
				"En pause" -> MangaState.PAUSED
				else -> null
			},
			author = root.selectFirst(".datas_more-authors-people")?.textOrNull(),
			chapters = run {
				val input = root.selectFirst("input[name=\"limit\"]") ?: return@run parseChapters(root)
				val max = input.attr("max").toInt()
				if (max <= 1) {
					parseChapters(root)
				} else {
					coroutineScope {
						val result = ArrayList(parseChapters(root))
						result.ensureCapacity(result.size * max)
						(1..max).map { i ->
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
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
				val a = div.selectFirstOrThrow("a:not([style*='display:none'])")
				val href = a.attrAsRelativeUrl("href")
				val title = div.selectFirstOrThrow(".chapter_volume").text()
				val name = div.selectFirst(".chapter_title")?.textOrNull()
				MangaChapter(
					id = generateUid(href),
					name = if (name != null && name != title) "$title: $name" else title,
					number = href.substringAfterLast('/').toFloatOrNull() ?: 0f,
					volume = 0,
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
}
