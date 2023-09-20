package org.koitharu.kotatsu.parsers.site.heancms.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms

@MangaSourceParser("YUGEN_MANGAS_ES", "Yugen Mangas Es", "es", ContentType.HENTAI)
internal class YugenMangasEs(context: MangaLoaderContext) :
	HeanCms(context, MangaSource.YUGEN_MANGAS_ES, "yugenmangas.net")
