package org.koitharu.kotatsu.parsers.site.foolslide.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser


@MangaSourceParser("SILENTSKYSCANS", "Silent Sky Scans", "en")
internal class SilentskyScans(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaSource.SILENTSKYSCANS, "reader.silentsky-scans.net") {
	override val pagination = false
}
