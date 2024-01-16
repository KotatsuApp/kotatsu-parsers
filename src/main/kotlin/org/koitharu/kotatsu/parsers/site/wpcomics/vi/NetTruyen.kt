package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser

@MangaSourceParser("NETTRUYEN", "NetTruyen", "vi")
internal class NetTruyen(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.NETTRUYEN, "www.nettruyenlive.com", 36) {
	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain(
		"www.nettruyenaz.com",
		"www.nettruyenlive.com",
		"www.nettruyenio.com",
		"www.nettruyento.com",
		"nettruyento.com",
		"nettruyenin.com",
	)
}
