package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SHADOWTRAD", "ShadowTrad", "fr")
internal class ShadowTrad(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SHADOWTRAD, "shadowtrad.net", 10)
