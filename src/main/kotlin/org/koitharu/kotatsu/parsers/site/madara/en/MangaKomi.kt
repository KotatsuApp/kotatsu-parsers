package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_KOMI", "MangaKomi", "en")
internal class MangaKomi(context: MangaLoaderContext) : MadaraParser( context, MangaSource.MANGA_KOMI, "mangakomi.io", pageSize = 18,)
