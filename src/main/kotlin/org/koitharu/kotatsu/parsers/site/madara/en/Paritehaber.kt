package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PARITEHABER", "Paritehaber", "en")
internal class Paritehaber(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PARITEHABER, "www.paritehaber.com", 10) {

	override val isNsfwSource = true
}
