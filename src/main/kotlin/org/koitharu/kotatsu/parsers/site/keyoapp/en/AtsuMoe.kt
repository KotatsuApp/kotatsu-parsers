package org.koitharu.kotatsu.parsers.site.keyoapp.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.keyoapp.KeyoappParser

@MangaSourceParser("ATSUMOE", "AtsuMoe", "en")
internal class AtsuMoe(context: MangaLoaderContext) :
    KeyoappParser(context, MangaParserSource.ATSUMOE, "atsu.moe")
