package org.koitharu.kotatsu.parsers.site.ru.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("SEIMANGA", "SeiManga", "ru")
internal class SeiMangaParser(
	context: MangaLoaderContext,
) : GroupleParser(context, MangaSource.SEIMANGA, 21) {

	override val configKeyDomain = ConfigKey.Domain(*domains)

	companion object {

		val domains = arrayOf(
			"seimanga.me",
		)
	}
}
