package org.koitharu.kotatsu.parsers.site.en.mtl

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.ContentType

@Broken
@MangaSourceParser("SOLARMTL", "SolarMTL", "en", type = ContentType.MANGA)
internal class SolarMTL(context: MangaLoaderContext):
    MTLParser(context, source = MangaParserSource.SOLARMTL, "solarmtl.com")
