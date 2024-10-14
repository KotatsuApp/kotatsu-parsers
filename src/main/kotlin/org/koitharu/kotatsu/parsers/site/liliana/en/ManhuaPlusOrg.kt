package org.koitharu.kotatsu.parsers.site.liliana.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser

@MangaSourceParser("MANHUAPLUSORG", "ManhuaPlus.org", "en")
internal class ManhuaPlusOrg(context: MangaLoaderContext) :
	LilianaParser(context, MangaParserSource.MANHUAPLUSORG, "manhuaplus.org")
