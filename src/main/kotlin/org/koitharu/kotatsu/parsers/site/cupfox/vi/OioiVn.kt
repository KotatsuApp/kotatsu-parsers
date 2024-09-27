package org.koitharu.kotatsu.parsers.site.cupfox.vi

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.cupfox.CupFoxParser

@Broken
@MangaSourceParser("OIOIVN", "OioiVn", "vi")
internal class OioiVn(context: MangaLoaderContext) :
	CupFoxParser(context, MangaParserSource.OIOIVN, "oioivn.com")
