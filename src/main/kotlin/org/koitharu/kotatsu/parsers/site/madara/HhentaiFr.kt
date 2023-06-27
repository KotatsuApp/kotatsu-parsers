package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.insertCookies
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import java.util.*


@MangaSourceParser("HHENTAIFR", "HhentaiFr", "fr")
internal class HhentaiFr(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.HHENTAIFR, "hhentai.fr") {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale.FRENCH

	override val isNsfwSource = true

	init {
		context.cookieJar.insertCookies(
			domain,
			"age_gate=32;",
		)
	}

	override fun String.asMangaState(): MangaState? = when (this) {
		"En Cours",
		-> MangaState.ONGOING

		"Terminé",
		-> MangaState.FINISHED

		else -> null
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".summary_content")
		val tags = postContent.getElementsContainingOwnText("Genre(s)")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			rating = postContent.selectFirstOrThrow(".post-rating")
				.selectFirstOrThrow(".total_votes").text().toFloat() / 5f,
			description = root.selectFirstOrThrow(".description-summary")
				.firstElementChild()?.html(),
			author = postContent.getElementsContainingOwnText("Auteur(s)")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			altTitle = postContent.getElementsContainingOwnText("Alternatif")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Statut")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}
}
