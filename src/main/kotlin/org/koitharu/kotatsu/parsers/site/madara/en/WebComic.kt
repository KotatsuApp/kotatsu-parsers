package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBCOMIC", "WebComic", "en")
internal class WebComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBCOMIC, "webcomic.me") {

	override val postreq = true
}
