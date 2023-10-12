package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import java.util.ArrayList
import java.util.Base64

@MangaSourceParser("MANGABUDDY", "Manga Buddy", "en")
internal class MangaBuddy(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.MANGABUDDY, "mangabuddy.com") {

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow("script:containsData(chapImages)")
		val images = script.data().substringAfter("'").substringBeforeLast("'").split(",")
		val pages = ArrayList<MangaPage>(images.size)
		for (image in images) {
			pages.add(
				MangaPage(
					id = generateUid(image),
					url = image,
					preview = null,
					source = source,
				),
			)
		}
		return pages

	}
}
