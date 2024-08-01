package org.koitharu.kotatsu.parsers.site.guya.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.guya.GuyaParser

@MangaSourceParser("DANKE", "DankeFursLesen", "en")
internal class Danke(context: MangaLoaderContext) :
	GuyaParser(context, MangaParserSource.DANKE, "danke.moe")
