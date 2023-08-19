package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAROCKTEAM", "Manga Rock (unoriginal)", "en")
internal class MangaRockTeam(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAROCKTEAM, "mangarock.team", 18)
