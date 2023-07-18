package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("APENASMAISUMYAOI", "ApenasmaisumYaoi", "pt")
internal class ApenasmaisumYaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.APENASMAISUMYAOI, "apenasmaisumyaoi.com") {

	override val isNsfwSource = true
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
