package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("CAT_300", "Cat300", "th")
internal class Cat300(context: MangaLoaderContext) : MadaraParser(context, MangaSource.CAT_300, "cat300.com") {

	override val datePattern = "MMMM dd, yyyy"
	override val isNsfwSource = true
}
