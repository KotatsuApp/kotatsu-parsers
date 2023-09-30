package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NETTRUYENMAX", "Nettruyen Max", "vi")
internal class Nettruyenmax(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.NETTRUYENMAX, "www.nettruyenus.com", 36) {
	override val listUrl = "/tim-truyen"
}
