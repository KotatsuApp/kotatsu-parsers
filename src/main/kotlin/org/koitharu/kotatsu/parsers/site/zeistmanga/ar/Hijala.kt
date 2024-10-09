package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("HIJALA", "Hijala", "ar")
internal class Hijala(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.HIJALA, "hijala.com")
