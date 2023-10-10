package org.koitharu.kotatsu.parsers.site.heancms.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.heancms.HeanCms

@MangaSourceParser("REAPERSCANSPT", "Reaper Scans", "pt")
internal class ReaperScansPt(context: MangaLoaderContext) :
	HeanCms(context, MangaSource.REAPERSCANSPT, "reaperscans.net") {
	override val configKeyDomain = ConfigKey.Domain("reaperscans.net", "reaperbr.online")
}
