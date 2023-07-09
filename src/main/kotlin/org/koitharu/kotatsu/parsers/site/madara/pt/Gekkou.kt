package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GEKKOU", "Gekkou", "pt")
internal class Gekkou(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GEKKOU, "gekkou.com.br", 10) {

	override val tagPrefix = "genero/"
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
