package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUAZONGHE", "Manhua Zonghe", "en")
internal class ManhuaZonghe(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHUAZONGHE, "manhuazonghe.com") {
	override val tagPrefix = "genre/"
	override val listUrl = "manhua/"
}
