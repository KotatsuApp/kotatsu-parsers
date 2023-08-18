package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAGREAT_ORG", "MangaGreat Org", "en")
internal class MangaGreatOrg(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAGREAT_ORG, "mangagreat.org")
