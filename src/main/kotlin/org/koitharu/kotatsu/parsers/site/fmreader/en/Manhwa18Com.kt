package org.koitharu.kotatsu.parsers.site.fmreader.en


import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.fmreader.FmreaderParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.text.SimpleDateFormat

@MangaSourceParser("MANHWA18COM", "Manhwa18 Com", "en", ContentType.HENTAI)
internal class Manhwa18Com(context: MangaLoaderContext) :
	FmreaderParser(context, MangaSource.MANHWA18COM, "manhwa18.com") {

	override val listeurl = "/tim-kiem"

	override val selectState = "div.info-item:contains(Status) span.info-value "
	override val selectAlt = "div.info-item:contains(Other name) span.info-value "
	override val selectTag = "div.info-item:contains(Genre) span.info-value a"
	override val datePattern = "dd/MM/yyyy"
	override val selectPage = "div#chapter-content img"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = doc.selectFirstOrThrow(selectdesc).html()

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

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectchapter).mapChapters(reversed = true) { i, a ->
			val href = a.attrAsRelativeUrl("href")
			val dateText = a.selectFirst(selectdate)?.text()?.substringAfter("- ")
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
