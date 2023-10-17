package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PEWPIECE", "PewPiece", "ar")
internal class PewPiece(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PEWPIECE, "pewpiece.com")
