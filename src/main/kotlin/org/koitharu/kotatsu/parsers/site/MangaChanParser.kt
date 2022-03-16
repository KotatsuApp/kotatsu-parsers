package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class MangaChanParser(override val context: MangaLoaderContext) : ChanParser(MangaSource.MANGACHAN) {

	override val configKeyDomain = ConfigKey.Domain("manga-chan.me", null)
}