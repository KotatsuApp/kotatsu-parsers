package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.manga18.Manga18Parser


@MangaSourceParser("PORNCOMIC18", "18 Porn Comic", "en", ContentType.HENTAI)
internal class PornComic18(context: MangaLoaderContext) :
	Manga18Parser(context, MangaSource.PORNCOMIC18, "18porncomic.com") {

	override val selectTag = "div.item:not(.info_label) div.info_value a"
}
