package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("ICHIROMANGA", "IchiroManga", "id")
internal class IchiroManga(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.ICHIROMANGA, "www.ichiromanga.my.id")
