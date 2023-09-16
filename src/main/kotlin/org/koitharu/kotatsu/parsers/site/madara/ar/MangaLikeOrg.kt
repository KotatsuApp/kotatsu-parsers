package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALIKE_ORG", "MangaLike Org", "ar")
internal class MangaLikeOrg(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALIKE_ORG, "mangalike.org", pageSize = 10)
