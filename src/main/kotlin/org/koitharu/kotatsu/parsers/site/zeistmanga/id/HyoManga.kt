package org.koitharu.kotatsu.parsers.site.zeistmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("HYOMANGA", "HyoManga", "id")
internal class HyoManga(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.HYOMANGA, "www.hyomanga.my.id") {
	override val mangaCategory = "Manga"
}
