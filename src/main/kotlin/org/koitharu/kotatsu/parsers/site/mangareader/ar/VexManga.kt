package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@Broken // Redirect to @VORTEXSCANS
@MangaSourceParser("VEXMANGA", "VexManga", "ar")
internal class VexManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.VEXMANGA, "vortexscans.org", pageSize = 10, searchPageSize = 10) {
	override val selectMangaList = ".listarchives .latest-recom"
	override val selectChapter = ".ulChapterList > a"
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = parseChapterDate(
					dateFormat,
					element.selectFirst(".chapterdate")?.text(),
				),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.startsWith("منذ") -> parseRelativeDate(date)
			d.startsWith("جديد") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"أيام",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet(
				"أسابيع",
			).anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis

			WordSet(
				"ساعة",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"دقائق",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("ثوان").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("أشهر").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			else -> 0
		}
	}
}
