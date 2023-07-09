package org.koitharu.kotatsu.parsers.site.madara.fr

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.text.SimpleDateFormat

@MangaSourceParser("TOONFR", "Toon Fr", "fr")
internal class ToonFr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TOONFR, "toonfr.com") {

	override val isNsfwSource = true
	override val tagPrefix = "webtoon-genre/"
	override val datePattern = "MMM d"

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val doc = webClient.httpPost(url, emptyMap()).parseHtml()

		return doc.select("li.wp-manga-chapter").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href") + "?style=list"

			// correct parse date missing a "."
			val date_org = li.selectFirst("span.chapter-release-date i")?.text() ?: "janv 1, 2000"
			val date_corect_parse = date_org
				.replace("Jan", "janv.")
				.replace("Févr", "févr.")
				.replace("Avr", "avr.")
				.replace("Juil", "juil.")
				.replace("Sept", "sept.")
				.replace("Nov", "nov.")
				.replace("Oct", "oct.")
				.replace("Déc", "déc.")
			MangaChapter(
				id = generateUid(href),
				url = href,
				name = a.text(),
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					date_corect_parse,
				),
				scanlator = null,
				source = source,
			)
		}
	}
}

