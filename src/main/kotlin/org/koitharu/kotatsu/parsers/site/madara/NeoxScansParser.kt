package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("NEOXSCANS", "NeoxScans", "pt")
internal class NeoxScansParser(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.NEOXSCANS, "neoxscans.net") {

	override val tagPrefix = "manga-genre/"

	override val datePattern: String = "dd/MM/yyyy"

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".post-content")
		val tags = postContent.getElementsContainingOwnText("Genre")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			largeCoverUrl = root.selectFirst("picture")
				?.selectFirst("img[src]")
				?.attrAsAbsoluteUrlOrNull("src"),
			description = (root.selectFirst(".post-content")
				?: root.selectFirstOrThrow(".manga-excerpt")).html(),
			author = postContent.getElementsContainingOwnText("Autor")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Status")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.main-col-inner")
			?.selectFirst("div.reading-content")
			?: throw ParseException("Root not found", fullUrl)
		return root.select("div.page-break").map { div ->
			val img = div.selectFirst("img") ?: div.parseFailed("Page image not found")
			val url = img.attr("src") ?: div.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override fun String.asMangaState() = when (trim().lowercase(Locale.forLanguageTag("pt"))) {
		"Em lançamento" -> MangaState.ONGOING
		"Completo" -> MangaState.FINISHED
		else -> null
	}
}
