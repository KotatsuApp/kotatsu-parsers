package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.assertNotNull
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow


@MangaSourceParser("ATLANTISSCAN", "Atlantisscan", "pt")
internal class Atlantisscan(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.ATLANTISSCAN, "atlantisscan.com") {

	override val datePattern = "dd/MM/yyyy"

	override fun String.asMangaState(): MangaState? = when (this) {
		"OnGoing",
		-> MangaState.ONGOING

		"finished",
		-> MangaState.FINISHED

		else -> null
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".summary_content")
		val tags = postContent.getElementsContainingOwnText("Genre(s) ")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			largeCoverUrl = root.selectFirst(".summary_image")
				?.selectFirst("img[data-src]")
				?.attrAsAbsoluteUrlOrNull("data-src")
				.assertNotNull("largeCoverUrl"),
			description = root.selectFirstOrThrow(".description-summary")
				.firstElementChild()?.html(),
			author = postContent.getElementsContainingOwnText("Author(s)")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			altTitle = postContent.getElementsContainingOwnText("Alternative")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Status")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}
}
