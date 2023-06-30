package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ALLPORN_COMIC", "All Porn Comic", "en")
internal class AllPornComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ALLPORN_COMIC, "allporncomic.com", pageSize = 24) {

	override val tagPrefix = "porncomic-genre/"

	override val isNsfwSource = true

}
