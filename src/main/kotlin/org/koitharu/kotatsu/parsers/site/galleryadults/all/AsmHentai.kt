package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.removeSuffix

@MangaSourceParser("ASMHENTAI", "AsmHentai", type = ContentType.HENTAI)
internal class AsmHentai(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.ASMHENTAI, "asmhentai.com") {
	override val selectGallery = ".preview_item"
	override val selectGalleryLink = ".image a"
	override val selectGalleryImg = ".image img"
	override val pathTagUrl = "/tags/?page="
	override val selectAuthor = "div.tags:contains(Artists:) .tag_list a span.tag"
	override val idImg = "fimg"
	override val listLanguage = arrayOf(
		"/english",
		"/japanese",
		"/chinese",
		"/turkish",
	)

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".tag")?.html()?.substringBefore("<") ?: it.html().substringBefore("<")
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}
}
