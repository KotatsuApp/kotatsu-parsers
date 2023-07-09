package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.insertCookies

@MangaSourceParser("HHENTAIFR", "HhentaiFr", "fr")
internal class HhentaiFr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HHENTAIFR, "hhentai.fr") {

	override val datePattern = "MMMM d, yyyy"
	override val isNsfwSource = true

	init {
		context.cookieJar.insertCookies(
			domain,
			"age_gate=32;",
		)
	}
}
