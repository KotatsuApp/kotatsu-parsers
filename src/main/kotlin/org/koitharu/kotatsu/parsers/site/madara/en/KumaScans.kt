package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KUMASCANS", "Retsu", "en")
internal class KumaScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KUMASCANS, "retsu.org") {
	override val tagPrefix = "genre/"
}
