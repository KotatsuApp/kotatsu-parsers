package org.koitharu.kotatsu.parsers.site.madara.ko

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("RAWDEX", "Raw Dex", "ko")
internal class RawDex(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RAWDEX, "rawdex.net", 40) {

	override val isNsfwSource = true
	override val sourceLocale: Locale = Locale.ENGLISH
}
