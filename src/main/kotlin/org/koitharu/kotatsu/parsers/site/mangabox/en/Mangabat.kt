package org.koitharu.kotatsu.parsers.site.mangabox.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser

@MangaSourceParser("HMANGABAT", "Manga Bat", "en")
internal class Mangabat(context: MangaLoaderContext) :
	MangaboxParser(context, MangaSource.HMANGABAT) {

	override val configKeyDomain = ConfigKey.Domain("h.mangabat.com", "readmangabat.com")

	override val otherDomain = "readmangabat.com"

	override val searchUrl = "/search/manga/"

	override val listUrl = "/manga-list-all"
	override val selectTagMap = "div.panel-category p.pn-category-row:not(.pn-category-row-border) a"
}
