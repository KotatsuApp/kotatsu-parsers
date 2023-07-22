package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("INDO18H", "Indo18h", "id")
internal class Indo18h(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.INDO18H, "indo18h.com", 24) {

	override val isNsfwSource = true
	override val withoutAjax = true
}
