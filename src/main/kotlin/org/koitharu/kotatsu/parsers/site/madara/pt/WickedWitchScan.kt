package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WICKEDWITCHSCAN", "Wicked Witch Scan", "pt")
internal class WickedWitchScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WICKEDWITCHSCAN, "wickedwitchscan.com", pageSize = 10) {
	override val postReq = true
}
