package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class MintMangaParser(override val context: MangaLoaderContext) : GroupleParser() {

	override val source = MangaSource.MINTMANGA
	override val defaultDomain: String = "mintmanga.live"
}