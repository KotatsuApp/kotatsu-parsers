package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.removeSuffix
import java.util.*

@MangaSourceParser("ASMHENTAI", "AsmHentai", type = ContentType.HENTAI)
internal class AsmHentai(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaParserSource.ASMHENTAI, "asmhentai.com") {

	override val selectGallery = ".preview_item"
	override val selectGalleryLink = ".image a"
	override val selectGalleryImg = ".image img"
	override val pathTagUrl = "/tags/?page="
	override val selectTags = ".tags_page ul.tags"
	override val selectAuthor = "div.tags:contains(Artists:) .tag_list a span.tag"
	override val idImg = "fimg"

	override suspend fun getFilterOptions() = super.getFilterOptions().copy(
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.JAPANESE,
			Locale.CHINESE,
			Locale("tr"),
		),
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
