package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl

@MangaSourceParser("MANGAJINX", "MangaJinx", "en")
internal class MangaJinx(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.MANGAJINX, "mangajinx.com") {
	override val listUrl = "search"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow("script:containsData(var chapImages)")
		val images = script.data().substringAfter("= \"").substringBefore("\";").split(",")
		return images.map {
			MangaPage(
				id = generateUid(it),
				url = it,
				preview = null,
				source = source,
			)
		}
	}
}
