package org.koitharu.kotatsu.parsers.site.ru.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@MangaSourceParser("READMANGA_RU", "ReadManga", "ru")
internal class ReadmangaParser(
	context: MangaLoaderContext,
) : GroupleParser(context, MangaParserSource.READMANGA_RU, 1) {

	override val configKeyDomain = ConfigKey.Domain(*domains)

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "https://$domain/")
		.build()

	companion object {

		val domains = arrayOf(
			"t.readmanga.io",
			"zz.readmanga.io",
			"readmanga.live",
			"readmanga.io",
			"readmanga.me",
		)
	}
}
