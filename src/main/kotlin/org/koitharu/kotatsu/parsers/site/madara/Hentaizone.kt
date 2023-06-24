package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*


@MangaSourceParser("HENTAIZONE", "Hentaizone", "fr")
internal class Hentaizone(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.HENTAIZONE, "hentaizone.xyz") {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH

	override val isNsfwSource = true

	override fun String.asMangaState(): MangaState? = when (this) {
		"OnGoing",
		-> MangaState.ONGOING

		"finished",
		-> MangaState.FINISHED

		else -> null
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".post-content")
		val tags = postContent.getElementsContainingOwnText("Genre(s)")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			rating = postContent.selectFirstOrThrow(".post-rating")
				.selectFirstOrThrow(".total_votes").text().toFloat() / 5f,
			description = root.selectFirst("div.description-summary")?.selectFirst("div.summary__content")?.select("p")
				?.filterNot { it.ownText().startsWith("A brief description") }?.joinToString { it.html() },
			author = postContent.getElementsContainingOwnText("Auteur(s)")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			altTitle = postContent.getElementsContainingOwnText("Alternatif")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Statut")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			chapters = chapters,
		)
	}


	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val root2 = doc.body().selectFirstOrThrow("div.content-area").selectFirstOrThrow("div.c-page")
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return root2.select("li").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")

			// correct parse date missing a "."
			val date_org = li.selectFirst("span.chapter-release-date i")?.text() ?: "janv 1, 2000"
			val date_corect_parse = date_org
				.replace("janv", "janv.")
				.replace("févr", "févr.")
				.replace("avr", "avr.")
				.replace("juil", "juil.")
				.replace("sept", "sept.")
				.replace("nov", "nov.")
				.replace("oct", "oct.")
				.replace("déc", "déc.")

			MangaChapter(
				id = generateUid(href),
				name = a.ownText(),
				number = i + 1,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					date_corect_parse,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
