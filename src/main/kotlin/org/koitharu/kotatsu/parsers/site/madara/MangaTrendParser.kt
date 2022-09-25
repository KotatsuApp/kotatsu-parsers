package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow

@MangaSourceParser("MANGATREND", "MangaTrend", "ar")
internal class MangaTrendParser(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.MANGATREND, "mangaatrend.com") {

	override val tagPrefix = "manga-genre/"

	override fun getFaviconUrl(): String {
		return "https://${getDomain()}/wp-content/uploads/2022/08/23UOmUDN_400x400-150x150.png"
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".post-content")
		val tags = postContent.getElementsContainingOwnText("التصنيفات")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			largeCoverUrl = root.selectFirst("summary_image")
				?.selectFirst("img[src]")
				?.attrAsAbsoluteUrlOrNull("src"),
			description = (root.selectFirst(".detail-content")
				?: root.selectFirstOrThrow(".manga-excerpt")).html(),
			author = postContent.getElementsContainingOwnText("المؤلف")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("الحالة")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}

	override fun String.asMangaState() = when (trim()) {
		"OnGoing" -> MangaState.ONGOING
		else -> null
	}
}