package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.Broken

@Broken // It has become obsolete and has been replaced by the new VyManga parser.
@MangaSourceParser("VYVYMANGA", "VyvyManga", "en")
internal class VyvyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.VYVYMANGA, "vyvymanga.org")
