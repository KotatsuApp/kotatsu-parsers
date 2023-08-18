package org.koitharu.kotatsu.parsers.site.sinmh.zh

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.sinmh.SinmhParser

@MangaSourceParser("IMITUI", "Imitui", "zh")
internal class Imitui(context: MangaLoaderContext) :
	SinmhParser(context, MangaSource.IMITUI, "www.imitui.com")
