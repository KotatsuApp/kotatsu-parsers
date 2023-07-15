package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale


@MangaSourceParser("REALMSCANS", "RealmScans", "en")
internal class RealmScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.REALMSCANS, pageSize = 30, searchPageSize = 50) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("realmscans.xyz")

	override val listUrl = "/series"
	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
}
