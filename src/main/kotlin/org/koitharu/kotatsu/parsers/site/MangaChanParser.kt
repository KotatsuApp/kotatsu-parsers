package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class MangaChanParser(override val context: MangaLoaderContext) : ChanParser() {

	override val defaultDomain = "manga-chan.me"
	override val source = MangaSource.MANGACHAN
}