package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArrayMap
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Favicon
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.SuspendLazy
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.Calendar
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("MANHWA18", "Manhwa18", "en")
class Manhwa18Parser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.MANHWA18, pageSize = 18, searchPageSize = 18) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manhwa18.net")

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	private val tagsMap = SuspendLazy(::parseTags)

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://${domain}/uploads/logos/logo-mini.png", 92, null),
			),
			domain,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val cardInfoElement = docs.selectFirst("div.series-information")
		val author = cardInfoElement?.selectFirst(".info-name:contains(Author(s))")?.parent()
			?.select("a")
			?.joinToString(", ") { it.text() }
		val availableTags = tagsMap.get()
		val tags = cardInfoElement?.selectFirst(".info-name:contains(Genre(s))")?.parent()
			?.select("a")
			?.mapNotNullToSet { availableTags[it.text().lowercase(Locale.ENGLISH)] }
		val state = cardInfoElement?.selectFirst(".info-name:contains(Status)")?.parent()
			?.selectFirst("a")
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
			chapters = docs.select(".card-body > .list-chapters > a").mapChapters(reversed = true) { index, element ->
				// attrAsRelativeUrl only return page url without the '/'
				val chapterUrl = element.attrAsAbsoluteUrlOrNull("href")?.toRelativeUrl(domain)
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
			},
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
			"week", "weeks" -> Calendar.WEEK_OF_YEAR
			"month", "months" -> Calendar.MONTH
			"year", "years" -> Calendar.YEAR
			else -> return 0
		}
		val cal = Calendar.getInstance()
		cal.add(timeUnit, -timeAmount)
		return cal.time.time
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val sortQuery = when (sortOrder) {
			SortOrder.ALPHABETICAL -> "az"
			SortOrder.POPULARITY -> "top"
			SortOrder.UPDATED -> "update"
			SortOrder.NEWEST -> "new"
			else -> ""
		}

		val tagQuery = tags?.joinToString(",") { it.key }.orEmpty()
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem?page=")
			append(page)
			if (!query.isNullOrEmpty()) {
				append("&q=")
				append(query.urlEncoded())
			}
			append("&accept_genres=$tagQuery")
			append("&sort=")
			append(sortQuery)
		}

		val docs = webClient.httpGet(url).parseHtml()

		return docs.select(".card-body .thumb-item-flow")
			.map {
				val titleElement = it.selectFirstOrThrow(".thumb_attr.series-title > a")
				val absUrl = titleElement.attrAsAbsoluteUrl("href")
				Manga(
					id = generateUid(absUrl.toRelativeUrl(domain)),
					title = titleElement.text(),
					altTitle = null,
					url = absUrl.toRelativeUrl(domain),
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
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(chapterUrl).parseHtml()
		return doc.requireElementById("chapter-content").select("img").mapNotNull {
			val url = it.attrAsRelativeUrlOrNull("data-src")
				?: it.attrAsRelativeUrlOrNull("src")
				?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = MangaSource.MANHWA18,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return tagsMap.get().values.toSet()
	}

	private suspend fun parseTags(): Map<String, MangaTag> {
		val doc = webClient.httpGet("https://$domain/tim-kiem?q=").parseHtml()
		val list = doc.getElementsByAttribute("data-genre-id")
		if (list.isEmpty()) {
			return emptyMap()
		}
		val result = ArrayMap<String, MangaTag>(list.size)
		for (item in list) {
			val id = item.attr("data-genre-id")
			val name = item.text()
			result[name.lowercase(Locale.ENGLISH)] = MangaTag(
				title = name.toTitleCase(Locale.ENGLISH),
				key = id,
				source = source,
			)
		}
		return result
	}
}
