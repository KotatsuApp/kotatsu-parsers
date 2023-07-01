package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TREE_MANGA", "Tree Manga", "en")
internal class TreeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.TREE_MANGA, "treemanga.com") {

	override val datePattern = "MM/dd/yyyy"

}
