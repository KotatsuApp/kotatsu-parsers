package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class MintMangaParser(override val context: MangaLoaderContext) : GroupleParser(MangaSource.MINTMANGA) {

	override val configKeyDomain = ConfigKey.Domain("mintmanga.live", null)
}