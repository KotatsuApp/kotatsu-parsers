package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class ReadmangaParser(override val context: MangaLoaderContext) : GroupleParser() {

	override val defaultDomain = "readmanga.io"
	override val source = MangaSource.READMANGA_RU
}