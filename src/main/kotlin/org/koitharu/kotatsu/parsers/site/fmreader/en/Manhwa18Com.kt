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
	FmreaderParser(context, MangaSource.MANHWA18COM, "manhwa18.com") {

	override val listUrl = "/tim-kiem"
	override val selectState = "div.info-item:contains(Status) span.info-value "
	override val selectAlt = "div.info-item:contains(Other name) span.info-value "
	override val selectTag = "div.info-item:contains(Genre) span.info-value a"
	override val datePattern = "dd/MM/yyyy"
	override val selectPage = "div#chapter-content img"
	override val selectBodyTag = "div.genres-menu a"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			if (!tags.isNullOrEmpty()) {
				append("/genre/")
				append(tag?.key.orEmpty())
				append("?page=")
				append(page.toString())
				append("&sort=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("views")
					SortOrder.UPDATED -> append("last_update")
					SortOrder.ALPHABETICAL -> append("name")
					else -> append("last_update")
				}
			} else {
				append(listUrl)
				append("?page=")
				append(page.toString())
				when {
					!query.isNullOrEmpty() -> {
						append("&q=")
						append(query.urlEncoded())
					}
				}
				append("&sort=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("views")
					SortOrder.UPDATED -> append("last_update")
					SortOrder.ALPHABETICAL -> append("name")
					else -> append("last_update")
				}

			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.thumb-item-flow").map { div ->
			val href = div.selectFirstOrThrow("div.series-title a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirstOrThrow("div.img-in-ratio").attr("data-bg")
					?: div.selectFirstOrThrow("div.img-in-ratio").attr("style").substringAfter("('")
						.substringBeforeLast("')"),
				title = div.selectFirstOrThrow("div.series-title").text().orEmpty(),
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

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
		return doc.select(selectBodyTag).mapNotNullToSet { a ->
			val href = a.attr("href").substringAfterLast("/")
			MangaTag(
				key = href,
				title = a.text(),
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
			when (it.text()) {
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
				number = i + 1,
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
