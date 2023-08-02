package org.koitharu.kotatsu.parsers.site.wpcomics.vi


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser


@MangaSourceParser("NETTRUYENMAX", "Nettruyenmax", "vi")
internal class Nettruyenmax(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.NETTRUYENMAX, "www.nettruyenmax.com", 35){

	override val listUrl = "/tim-truyen"
}
