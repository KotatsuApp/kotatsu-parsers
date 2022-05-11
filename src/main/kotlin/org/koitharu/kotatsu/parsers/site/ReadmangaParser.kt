package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("READMANGA_RU", "ReadManga", "ru")
internal class ReadmangaParser(
	override val context: MangaLoaderContext,
) : GroupleParser(MangaSource.READMANGA_RU, "readmangafun") {

	override val configKeyDomain = ConfigKey.Domain(
		"readmanga.io",
		arrayOf("readmanga.io", "readmanga.live", "readmanga.me"),
	)
}