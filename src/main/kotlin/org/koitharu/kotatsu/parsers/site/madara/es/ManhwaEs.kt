package org.koitharu.kotatsu.parsers.site.madara.es

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("MANHWA_ES", "Manhwa Es", "es")
internal class ManhwaEs(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWA_ES, "manhwa-es.com", 10) {

	override val withoutAjax = true
	override val datePattern = "d 'de' MMMM"

	override suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val link = href + stylepage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()

			val name = li.selectFirstOrThrow(".mini-letters a").text()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1,
				url = link,
				uploadDate = if (dateText == "¡Recién publicado!") {
					parseChapterDate(
						dateFormat,
						"today",
					)
				} else {
					parseChapterDate(
						dateFormat,
						dateText,
					)
				},
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}
}
