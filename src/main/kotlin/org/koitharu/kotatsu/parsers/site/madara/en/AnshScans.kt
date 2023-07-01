package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ANSHSCANS", "Ansh Scans", "en")
internal class AnshScans(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ANSHSCANS, "anshscans.org", 10){

	override val tagPrefix = "genre/"

}
