package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PORNCOMIXONLINE_NET", "Porn Comix Online Net", "en", ContentType.HENTAI)
internal class PornComixOnlineNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PORNCOMIXONLINE_NET, "www.porncomixonline.net") {

	override val listUrl = "m-comic/"
}
