package org.koitharu.kotatsu.parsers.site.en.MTL

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.site.en.MTL.MTLParser

@MangaSourceParser("SOLARMTL", "SolarMTL", "en", type = ContentType.MANGA)
internal class SolarMTL(context: MangaLoaderContext):
    MTLParser(context, source = MangaParserSource.SOLARMTL, "solarmtl.com")