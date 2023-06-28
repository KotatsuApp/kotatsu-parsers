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
import java.util.*


@MangaSourceParser("HIPERCOOL", "Hipercool", "pt")
internal class Hipercool(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.HIPERCOOL, "hipercool.xyz", pageSize = 20) {

	override val datePattern = "MMMM d, yyyy"

	override val tagPrefix = "manga-tag/"

	override val isNsfwSource = true

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".main-col")
		val tags = postContent.getElementsByClass("tags-content")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags

		return manga.copy(


			largeCoverUrl = root.selectFirst("picture")
				?.selectFirst("img[data-src]")
				?.attrAsAbsoluteUrlOrNull("data-src"),
			description = root.selectFirst("div.description-summary")?.selectFirst("div.summary__content")?.select("p")
				?.filterNot { it.ownText().startsWith("A brief description") }?.joinToString { it.html() },
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}

	override fun String.asMangaState() = when (trim().lowercase(Locale.ROOT)) {
		"em lançamento" -> MangaState.ONGOING

		else -> null
	}

}
