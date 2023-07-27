package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("EROMIAU", "Eromiau", "es", ContentType.HENTAI)
internal class Eromiau(context: MangaLoaderContext) : MadaraParser(context, MangaSource.EROMIAU, "www.eromiau.com", 10)
