package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANHWA18", "Manhwa18", "en")
class Manhwa18Parser(override val context: MangaLoaderContext) : PagedMangaParser(MangaSource.MANHWA18, pageSize = 20, searchPageSize = 20) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manhwa18.net", null)

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL)

	override suspend fun getFavicons(): Favicons {
		return Favicons(listOf(
			Favicon("https://${getDomain()}/uploads/logos/logo-mini.png", 92, null)
		), getDomain())
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml()
		val cardInfoElement = docs.selectFirst(".card .manga-info")
		val author = cardInfoElement?.selectFirst("b:contains(Author(s))")?.parent()
			?.select("a.btn")
			?.joinToString(", ") { it.text() }
		val tags = cardInfoElement?.selectFirst("b:contains(Genre(s))")?.parent()
			?.select("a.btn")
			?.mapToSet { MangaTag(it.text(), it.text().lowercase(), MangaSource.MANHWA18) }
		val state = cardInfoElement?.selectFirst("b:contains(Status)")?.parent()
			?.selectFirst("a.btn")
			?.let {
				when (it.text()) {
					"On going" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					else -> null
				}
			}

		return manga.copy(
			altTitle = cardInfoElement?.selectFirst("b:contains(Other names)")?.parent()?.ownText()?.removePrefix(": "),
			author = author,
			description = docs.selectFirst(".series-summary .summary-content")?.html(),
			tags = tags.orEmpty(),
			state = state,
			chapters = docs.select(".card-body > .list-chapters > a").asReversed().mapChapters { index, element ->
				// attrAsRelativeUrl only return page url without the '/'
				val chapterUrl = element.attrAsAbsoluteUrlOrNull("href")?.toRelativeUrl(getDomain())
					?: return@mapChapters null
				val uploadDate = parseUploadDate(element.selectFirst(".chapter-time")?.text())
				MangaChapter(
					id = generateUid(chapterUrl),
					name = element.selectFirst(".chapter-name")?.text().orEmpty(),
					number = index + 1,
					url = chapterUrl,
					scanlator = null,
					uploadDate = uploadDate,
					branch = null,
					source = MangaSource.MANHWA18,
				)
			}
		)
	}

	// 7 minutes ago
	// 5 hours ago
	// 2 days ago
	// 2 weeks ago
	// 4 years ago
	private fun parseUploadDate(timeStr: String?): Long {
		timeStr ?: return 0

		val timeWords = timeStr.split(' ')
		if (timeWords.size != 3) return 0
		val timeWord = timeWords[1]
		val timeAmount = timeWords[0].toIntOrNull() ?: return 0
		val timeUnit = when (timeWord) {
			"minute", "minutes" -> Calendar.MINUTE
			"hour", "hours" -> Calendar.HOUR
			"day", "days" -> Calendar.DAY_OF_YEAR
			"month", "months" -> Calendar.MONTH
			"year", "years" -> Calendar.YEAR
			else -> return 0
		}
		val cal = Calendar.getInstance()
		cal.add(timeUnit, timeAmount)
		return cal.time.time
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val sortQuery = when(sortOrder) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "views"
			SortOrder.UPDATED -> "last_update"
			else -> ""
		}

		val sortType = if (sortOrder == SortOrder.ALPHABETICAL) "ASC" else "DESC"
		val tagQuery = tags?.joinToString(",") { it.key }.orEmpty()
		val url = buildString {
			append("https://")
			append(getDomain())
			append("/manga-list.html?listType=pagination&page=")
			append(page)
			append("&artist=&author=&group=&m_status=&name=")
			append(query?.urlEncoded().orEmpty())
			append("&genre=$tagQuery")
			append("&ungenre=")
			append("&sort=")
			append(sortQuery)
			append("&sort_type=")
			append(sortType)
		}

		val docs = context.httpGet(url).parseHtml()
		val actualPage = docs.selectFirst("ul.pagination  a.active")?.text()?.toIntOrNull()
		if (actualPage != page) {
			return emptyList()
		}

		return docs.select(".card-body .thumb-item-flow")
			.map {
				val titleElement = it.selectFirstOrThrow(".thumb_attr.series-title > a")
				val absUrl = titleElement.attrAsAbsoluteUrl("href")
				Manga(
					id = generateUid(absUrl.toRelativeUrl(getDomain())),
					title = titleElement.text(),
					altTitle = null,
					url = absUrl.toRelativeUrl(getDomain()),
					publicUrl = absUrl,
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = it.selectFirst("div.img-in-ratio")?.attrAsAbsoluteUrl("data-bg").orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					largeCoverUrl = null,
					description = null,
					source = MangaSource.MANHWA18,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(getDomain())
		return context.httpGet(chapterUrl).parseHtml()
			.select("div.chapter-content > img").map {
				val url = it.attrAsRelativeUrlOrNull("src").orEmpty()
				MangaPage(
					id = generateUid(url),
					url = url,
					referer = chapterUrl,
					preview = null,
					source = MangaSource.MANHWA18,
				)
			}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return context.httpGet("https://${getDomain()}/").parseHtml().selectFirstOrThrow(".genres-menu")
			?.select("a.genres-item").orEmpty()
			.mapToSet {
				MangaTag(
					title = it.text(),
					key = it.text().lowercase(),
					source = MangaSource.MANHWA18,
				)
			}
	}
}