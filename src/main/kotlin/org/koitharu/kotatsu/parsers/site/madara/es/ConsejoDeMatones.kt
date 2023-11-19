package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("CONSEJODEMATONES", "ConsejoDeMatones", "es")
internal class ConsejoDeMatones(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.CONSEJODEMATONES, "www.consejodematones.xyz")
