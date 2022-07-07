package org.koitharu.kotatsu.parsers.site.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("MINTMANGA", "MintManga", "ru")
internal class MintMangaParser(
	override val context: MangaLoaderContext,
) : GroupleParser(MangaSource.MINTMANGA, "mintmangafun", 2) {

	override val configKeyDomain = ConfigKey.Domain(
		"mintmanga.live",
		arrayOf("mintmanga.live", "mintmanga.com"),
	)
}