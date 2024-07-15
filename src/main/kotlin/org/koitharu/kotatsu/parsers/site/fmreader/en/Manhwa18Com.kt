package org.koitharu.kotatsu.parsers.site.fmreader.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("MANHWA18COM", "Manhwa18.com", "en", ContentType.HENTAI)
internal class Manhwa18Com(context: MangaLoaderContext) :
	FmreaderParser(context, MangaParserSource.MANHWA18COM, "manhwa18.com") {

	override val listUrl = "/tim-kiem"
	override val selectState = "div.info-item:contains(Status) span.info-value "
	override val selectAlt = "div.info-item:contains(Other name) span.info-value "
	override val selectTag = "div.info-item:contains(Genre) span.info-value a"
	override val datePattern = "dd/MM/yyyy"
	override val selectPage = "div#chapter-content img"
	override val selectBodyTag = "div.advanced-wrapper .genre_label"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/tim-kiem?page=")
			append(page.toString())

			when (filter) {
				is MangaListFilter.Search -> {
					append("&q=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					append("&accept_genres=")
					append(filter.tags.joinToString(",") { it.key })

					append("&reject_genres=")
					append(filter.tagsExclude.joinToString(",") { it.key })

					append("&sort=")
					append(
						when (filter.sortOrder) {
							SortOrder.ALPHABETICAL -> "az"
							SortOrder.ALPHABETICAL_DESC -> "za"
							SortOrder.POPULARITY -> "top"
							SortOrder.UPDATED -> "update"
							SortOrder.NEWEST -> "new"
							SortOrder.RATING -> "like"
						},
					)

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "1"
								MangaState.FINISHED -> "3"
								MangaState.PAUSED -> "2"
								else -> ""
							},
						)
					}
				}

				null -> append("&sort=update")
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select(selectBodyTag).mapNotNullToSet { label ->
			val key = label.attr("data-genre-id")
			MangaTag(
				key = key,
				title = label.selectFirstOrThrow(".gerne-name").text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val desc = doc.selectFirstOrThrow(selectDesc).html()
		val stateDiv = doc.selectFirst(selectState)
		val state = stateDiv?.let {
			when (it.text().lowercase()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val alt = doc.body().selectFirst(selectAlt)?.text()?.replace("Other name", "")
		val auth = doc.body().selectFirst(selectAut)?.text()
		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfter("manga-list-genre-").substringBeforeLast(".html"),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = auth,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun getChapters(doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, a ->
			val href = a.attrAsRelativeUrl("href")
			val dateText = a.selectFirst(selectDate)?.text()?.substringAfter("- ")
			MangaChapter(
				id = generateUid(href),
				name = a.selectFirstOrThrow("div.chapter-name").text(),
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
