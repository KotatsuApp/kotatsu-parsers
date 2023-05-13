package org.koitharu.kotatsu.parsers.site.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("SELFMANGA", "SelfManga", "ru")
internal class SelfMangaParser(
	context: MangaLoaderContext,
) : GroupleParser(context, MangaSource.SELFMANGA, 3) {

	override val configKeyDomain = ConfigKey.Domain("selfmanga.live", null)

}
