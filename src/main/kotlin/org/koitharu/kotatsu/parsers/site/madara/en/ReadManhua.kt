package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("READMANHUA", "Read Manhua", "en")
internal class ReadManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.READMANHUA, "readmanhua.net", 20) {

	override val isNsfwSource = true
	override val postreq = true
	override val datePattern = "d MMM yy"
}
