package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("LIBREBOOK", "LibreBook", "ru")
internal class LibreBookParser(
	override val context: MangaLoaderContext,
) : GroupleParser(MangaSource.LIBREBOOK, "librebookfun") {

	override val configKeyDomain = ConfigKey.Domain("librebook.me", null)
}
