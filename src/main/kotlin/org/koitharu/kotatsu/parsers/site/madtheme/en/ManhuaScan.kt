package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@Broken
@MangaSourceParser("MANHUASCAN", "kaliscan.io", "en")
internal class ManhuaScan(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.MANHUASCAN, "manhuascan.io") {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "search"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)
			when (filter) {

				is MangaListFilter.Search -> {
					append("?sort=updated_at&q=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					append("?sort=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("views")
						SortOrder.UPDATED -> append("updated_at")
						SortOrder.ALPHABETICAL -> append("name")
						SortOrder.NEWEST -> append("created_at")
						SortOrder.RATING -> append("rating")
						else -> append("updated_at")
					}
					if (filter.tags.isNotEmpty()) {
						filter.tags.forEach {
							append("&")
							append("include[]".urlEncoded())
							append("=")
							append(it.key)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								else -> "all"
							},
						)
					}

				}

				null -> append("?sort=updated_at")
			}

			append("&page=")
			append(page.toString())
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.book-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("div.meta").selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.meta span.score").ownText().toFloatOrNull()?.div(5f)
					?: RATING_UNKNOWN,
				tags = doc.body().select("div.meta div.genres span").mapNotNullToSet { span ->
					MangaTag(
						key = span.attr("class"),
						title = span.text().toTitleCase(),
						source = source,
					)
				},
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val id = doc.selectFirstOrThrow("script:containsData(bookId)").data().substringAfter("bookId = ")
			.substringBefore(";")
		val docChapter = webClient.httpGet("https://$domain/service/backend/chaplist/?manga_id=$id").parseHtml()
		return docChapter.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst(selectDate)?.text()
			MangaChapter(
				id = generateUid(href),
				name = li.selectFirstOrThrow(".chapter-title").text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
