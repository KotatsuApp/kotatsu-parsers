package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NHATTRUYENVN", "NhatTruyenVN", "vi")
internal class NhatTruyenVN(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NHATTRUYENVN, "nhattruyenvn.com", 36) {
	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(
		"nhattruyenvn.com", "www.nhattruyenss.net",
	)
}
