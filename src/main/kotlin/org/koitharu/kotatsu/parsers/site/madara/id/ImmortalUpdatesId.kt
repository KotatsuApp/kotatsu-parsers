package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("IMMORTALUPDATESID", "Immortal Updates Id", "id")
internal class ImmortalUpdatesId(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.IMMORTALUPDATESID, "immortalupdates.id") {

	override val datePattern = "d MMMM yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
