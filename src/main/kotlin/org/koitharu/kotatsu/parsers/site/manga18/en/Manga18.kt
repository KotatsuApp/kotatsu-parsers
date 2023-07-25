package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.Manga18Parser


@MangaSourceParser("MANGA18", "Manga18", "en")
internal class Manga18(context: MangaLoaderContext) :
	Manga18Parser(context, MangaSource.MANGA18, "manga18.club") {

	override val isNsfwSource = true
}
