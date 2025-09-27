package org.koitharu.kotatsu.parsers.site.atsu

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@MangaSourceParser("ATSU", "AtsuMoe", "en")
internal class AtsuMoeParser(context: MangaLoaderContext) :
    KeyoappParser(context, MangaParserSource.ATSU, "atsu.moe")
