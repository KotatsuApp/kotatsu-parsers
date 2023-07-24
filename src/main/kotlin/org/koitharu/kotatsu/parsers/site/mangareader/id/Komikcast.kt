package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.WordSet
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@MangaSourceParser("KOMIKCAST", "Komikcast", "id")
internal class Komikcast(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKCAST, "komikcast.io", pageSize = 60, searchPageSize = 28) {

	override val listUrl = "/daftar-komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select("#chapter-wrapper > li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst("a.chapter-link-item")?.ownText().orEmpty(),
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = parseChapterDate(
					dateFormat,
					element.selectFirst("div.chapter-link-time")?.text(),
				),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	override suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		/// set if is table

		val tagMap = getOrCreateTagMap()

		val tags = docs.select(".komik_info-content-genre > a").mapNotNullToSet { tagMap[it.text()] }

		val state = docs.selectFirst(".komik_info-content-meta span:contains(Status)")?.lastElementChild()

		val mangaState = state?.let {
			when (it.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			}
		}


		val author = docs.selectFirst(".komik_info-content-meta span:contains(Author)")?.lastElementChild()?.text()

		val nsfw =
			docs.selectFirst(".restrictcontainer") != null || docs.selectFirst(".info-right .alr") != null || docs.selectFirst(
				".postbody .alr",
			) != null

		return manga.copy(
			description = docs.selectFirst("div.komik_info-description-sinopsis")?.text(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = tags,
			chapters = chapters,
		)
	}


	override fun parseMangaList(docs: Document): List<Manga> {
		return docs.select("div.list-update_item").mapNotNull {
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN

			val name = it.selectFirst("h3.title")?.text().orEmpty()
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = name,
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirst("img.ts-post-image")?.imageUrl().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		val test = docs.select("script:containsData(ts_reader)")
		if (test.isNullOrEmpty()) {
			return docs.select("div#chapter_body img").map { img ->
				val url = img.imageUrl()
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
			val script = docs.selectFirstOrThrow("script:containsData(ts_reader)")
			val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')')).getJSONArray("sources")
				.getJSONObject(0).getJSONArray("images")
			val pages = ArrayList<MangaPage>(images.length())
			for (i in 0 until images.length()) {
				pages.add(
					MangaPage(
						id = generateUid(images.getString(i)),
						url = images.getString(i),
						preview = null,
						source = source,
					),
				)
			}

			return pages

		}

	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0
		return when {
			date.endsWith(" ago", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"day",
				"days",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"mins",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
