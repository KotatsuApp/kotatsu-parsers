package org.koitharu.kotatsu.parsers.site.cupfox.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.cupfox.CupFoxParser

@MangaSourceParser("FRMANGA", "FrManga", "fr")
internal class FrManga(context: MangaLoaderContext) :
	CupFoxParser(context, MangaParserSource.FRMANGA, "www.frmanga.com")
