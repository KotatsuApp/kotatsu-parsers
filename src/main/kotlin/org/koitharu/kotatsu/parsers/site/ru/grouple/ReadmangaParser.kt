package org.koitharu.kotatsu.parsers.site.ru.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("READMANGA_RU", "ReadManga", "ru")
internal class ReadmangaParser(
	context: MangaLoaderContext,
) : GroupleParser(context, MangaSource.READMANGA_RU, 1) {

	override val configKeyDomain = ConfigKey.Domain(
		"readmanga.live",
		"readmanga.io",
		"readmanga.me",
	)
}
