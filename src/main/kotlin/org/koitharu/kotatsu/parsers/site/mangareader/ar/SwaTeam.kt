package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("SWATEAM", "Swa Team", "ar")
internal class SwaTeam(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SWATEAM, pageSize = 42, searchPageSize = 39) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("swateam.me")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale("ar", "AR"))


	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapters = docs.select("div.bixbox li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst("a")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = chapterDateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		/// set if is table

		val states = docs.selectFirst("div.spe span:contains(Ongoing)")?.text()

		val state = if (states.isNullOrEmpty()) {
			"Completed"
		} else {
			"Ongoing"
		}

		val mangaState = state.let {
			when (it) {
				"Ongoing" -> MangaState.ONGOING

				"Completed" -> MangaState.FINISHED

				else -> null
			}
		}


		val author = docs.selectFirst("span.author i")?.text()

		val nsfw = docs.selectFirst(".restrictcontainer") != null
				|| docs.selectFirst(".info-right .alr") != null
				|| docs.selectFirst(".postbody .alr") != null

		return manga.copy(
			description = docs.selectFirst("span.desc")?.html(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = docs.select("div.spe a[rel*=tag]").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			chapters = chapters,
		)
	}
}
