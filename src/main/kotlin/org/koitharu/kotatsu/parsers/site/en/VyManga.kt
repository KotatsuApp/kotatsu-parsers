package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("VYMANGA", "VyManga", "en")
internal class VyManga(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.VYMANGA, pageSize = 36) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("vymanga.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search?search_po=0&q=")

			filter.query?.let {
				append(filter.query.urlEncoded())
			}

			append("&author_po=0&author=")

			// filter.author?.let {
			// 	append(filter.author.urlEncoded())
			// }

			append("&completed=")
			filter.states.oneOrThrowIfMany()?.let {
				append(
					when (it) {
						MangaState.ONGOING -> "0"
						MangaState.FINISHED -> "1"
						else -> ""
					},
				)
			}

			append("&sort=")
			when (order) {
				SortOrder.POPULARITY -> append("viewed&sort_type=desc")
				SortOrder.POPULARITY_ASC -> append("viewed&sort_type=asc")
				SortOrder.RATING -> append("scored&sort_type=desc")
				SortOrder.RATING_ASC -> append("scored&sort_type=asc")
				SortOrder.NEWEST -> append("created_at&sort_type=desc")
				SortOrder.NEWEST_ASC -> append("created_at&sort_type=asc")
				SortOrder.UPDATED -> append("updated_at&sort_type=desc")
				SortOrder.UPDATED_ASC -> append("updated_at&sort_type=asc")
				else -> append("updated_at&sort_type=desc")
			}

			filter.tags.forEach { tag ->
				append("&genre[]=")
				append(tag.key)
			}

			filter.tagsExclude.forEach { tagsExclude ->
				append("&exclude_genre[]=")
				append(tagsExclude.key)
			}

			append("&page=")
			append(page.toString())
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select(".comic-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = div.selectFirst(".comic-image img")?.src().orEmpty(),
				title = div.selectFirst(".comic-title")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/search").parseHtml()
		return doc.select("#advance-search .check-genre .d-flex").mapToSet {
			MangaTag(
				key = it.selectFirstOrThrow(".checkbox-genre").attr("data-value"),
				title = it.selectFirstOrThrow("label").text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val simpleDateFormat = SimpleDateFormat("MMM dd, yyy", sourceLocale)
		return manga.copy(
			tags = doc.select("div.col-md-7 p a[href*=genre]").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('/'),
					title = a.text(),
					source = source,
				)
			},
			description = doc.selectFirst("div.summary p.content")?.text().orEmpty(),
			state = when (doc.selectLast("div.col-md-7 p:contains(Status) span")?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				else -> null
			},
			rating = doc.selectFirst("div.col-md-7 p:contains(Rating)")?.text()?.substringAfterLast(':')
				?.substringBefore('/')?.toFloat() ?: RATING_UNKNOWN,
			chapters = doc.select("div.list div.list-group a").mapChapters(reversed = true) { i, a ->
				val url = a.attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = a.selectFirst("span")?.text() ?: "Chapter ${i + 1}",
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = parseChapterDate(
						simpleDateFormat,
						a.selectFirst("p")?.text(),
					),
					branch = null,
					source = source,
				)
			},
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") -> parseRelativeDate(date)
			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("week", "weeks").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("img.d-block").map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
