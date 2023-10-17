package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KOINOBORISCAN", "KoinoboriScan", "es")
internal class KoinoboriScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KOINOBORISCAN, "koinoboriscan.com") {
	override val postReq = true
}
