package org.koitharu.kotatsu.parsers.site.heancms.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms

@MangaSourceParser("TEMPLESCAN", "TempleScan", "en")
internal class TempleScan(context: MangaLoaderContext) :
	HeanCms(context, MangaSource.TEMPLESCAN, "templescan.net") {
	override val pathManga = "comic"
}
