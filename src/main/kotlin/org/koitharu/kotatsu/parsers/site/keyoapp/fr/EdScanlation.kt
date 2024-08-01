package org.koitharu.kotatsu.parsers.site.keyoapp.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@MangaSourceParser("EDSCANLATION", "EdScanlation", "fr")
internal class EdScanlation(context: MangaLoaderContext) :
	KeyoappParser(context, MangaParserSource.EDSCANLATION, "edscanlation.fr")
