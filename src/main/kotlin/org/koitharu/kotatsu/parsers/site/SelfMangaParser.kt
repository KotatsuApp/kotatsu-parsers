package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class SelfMangaParser(override val context: MangaLoaderContext) : GroupleParser() {

	override val defaultDomain = "selfmanga.live"
	override val source = MangaSource.SELFMANGA
}