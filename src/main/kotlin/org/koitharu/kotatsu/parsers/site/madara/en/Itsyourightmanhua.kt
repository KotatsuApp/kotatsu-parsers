package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ITSYOURIGHTMANHUA", "ItsYouRightManhua", "en")
internal class Itsyourightmanhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ITSYOURIGHTMANHUA, "itsyourightmanhua.com", 10)
