package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser
import org.koitharu.kotatsu.parsers.Broken

@Broken // Website closed
@MangaSourceParser("SOBATMANKU", "Sobatmanku", "id")
internal class Sobatmanku(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.SOBATMANKU, "www.sobatmanku19.cab")
