package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("KOMIKCAST", "KomikCast", "id")
internal class Komikcast(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKCAST, "komikcast.cz", pageSize = 60, searchPageSize = 28) {

	override val listUrl = "/daftar-komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {

				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append(listUrl)
					append("/page/")
					append(page.toString())
					append("/?type=")
					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "&orderby=titleasc"
							SortOrder.ALPHABETICAL_DESC -> "&orderby=titledesc"
							SortOrder.POPULARITY -> "&orderby=popular"
							SortOrder.UPDATED -> "" // To get the Updated list, you don't need "orderby" in the url.
							else -> ""
						},
					)
					val tagKey = "genre[]".urlEncoded()
					val tagQuery =
						if (filter.tags.isEmpty()) ""
						else filter.tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
					append(tagQuery)

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							when (it) {
								MangaState.ONGOING -> append("Ongoing")
								MangaState.FINISHED -> append("Completed")
								else -> append("")
							}
						}
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select("#chapter-wrapper > li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a.chapter-link-item")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst("a.chapter-link-item")?.ownText().orEmpty(),
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = parseChapterDate(
					dateFormat,
					element.selectFirst("div.chapter-link-time")?.text(),
				),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val tagMap = getOrCreateTagMap()
		val tags = docs.select(".komik_info-content-genre > a").mapNotNullToSet { tagMap[it.text()] }
		val state = docs.selectFirst(".komik_info-content-meta span:contains(Status)")?.html()
		val mangaState = if (state!!.contains("Ongoing")) {
			MangaState.ONGOING
		} else {
			MangaState.FINISHED
		}
		val author = docs.selectFirst(".komik_info-content-meta span:contains(Author)")?.lastElementChild()?.text()
		val nsfw =
			docs.selectFirst(".restrictcontainer") != null || docs.selectFirst(".info-right .alr") != null || docs.selectFirst(
				".postbody .alr",
			) != null

		return manga.copy(
			description = docs.selectFirst("div.komik_info-description-sinopsis")?.text(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = tags,
			chapters = chapters,
		)
	}

	override fun parseMangaList(docs: Document): List<Manga> {
		return docs.select("div.list-update_item").mapNotNull {
			val a = it.selectFirstOrThrow("a.data-tooltip")
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN
			val name = it.selectFirst("h3.title")?.text().orEmpty()
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = name,
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirst("img.ts-post-image")?.src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val test = docs.select("script:containsData(ts_reader)")
		if (test.isNullOrEmpty()) {
			return docs.select("div#chapter_body img").map { img ->
				val url = img.requireSrc().toRelativeUrl(domain)
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
			val script = docs.selectFirstOrThrow("script:containsData(ts_reader)")
			val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')')).getJSONArray("sources")
				.getJSONObject(0).getJSONArray("images")
			val pages = ArrayList<MangaPage>(images.length())
			for (i in 0 until images.length()) {
				pages.add(
					MangaPage(
						id = generateUid(images.getString(i)),
						url = images.getString(i),
						preview = null,
						source = source,
					),
				)
			}
			return pages
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0
		return when {
			date.endsWith(" ago", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet(
				"day",
				"days",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"mins",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
