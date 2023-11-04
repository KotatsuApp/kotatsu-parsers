package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.removeSuffix

@MangaSourceParser("HENTAIROX", "HentaiRox", type = ContentType.HENTAI)
internal class HentaiRox(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIROX, "hentairox.com") {
	override val selectGalleryImg = ".inner_thumb img"
	override val selectTags = ".gtags"
	override val selectTag = "li:contains(Tags:)"
	override val selectAuthor = "li:contains(Artists:) span.item_name"
	override val selectLanguageChapter = "li:contains(Languages:) .item_name"
	override val listLanguage = arrayOf(
		"/english",
		"/french",
		"/japanese",
		"/spanish",
		"/russian",
		"/korean",
		"/german",
	)

	override fun Element.parseTags() = select("a.tag, .gallery_title a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".item_name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}
}
