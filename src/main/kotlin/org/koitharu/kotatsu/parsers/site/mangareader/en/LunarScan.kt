package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale


@MangaSourceParser("LUNAR_SCAN", "Lunar Scan", "en")
internal class LunarScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LUNAR_SCAN, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("lunarscan.org")

	override val listUrl = "/series"

	override val isNsfwSource: Boolean = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
}
