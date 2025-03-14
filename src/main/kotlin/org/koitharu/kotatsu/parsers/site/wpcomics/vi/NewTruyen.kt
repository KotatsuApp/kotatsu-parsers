package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NEWTRUYEN", "NewTruyen", "vi")
internal class NewTruyen(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NEWTRUYEN, "newtruyen5.com", 36) {

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val storyID = doc.selectFirst("input#storyID")?.attr("value")
			?: throw ParseException("Story ID not found", fullUrl)
		val chaptersDeferred = async { getChapterList(storyID) }
		val tagsElement = doc.select("p.col-xs-12 a.tr-theloai")
		val mangaTags = tagsElement.map {
			MangaTag(
				title = it.text(),
				key = it.attr("href").substringAfterLast('/'),
				source = source,
			)
		}.toSet()
		val author = doc.body().select(selectAut).textOrNull()

		manga.copy(
			description = doc.selectFirst(selectDesc)?.html(),
			authors = setOfNotNull(author),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			chapters = chaptersDeferred.await(),
		)
	}

	private suspend fun getChapterList(storyID: String): List<MangaChapter> {
		val url = "/Story/ListChapterByStoryID?storyID=$storyID"
		val fullUrl = url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("li.row ").mapChapters(reversed = true) { i, li ->
			val chapter = li.select("div.col-xs-5.chapter")
			val a = chapter.select("a").firstOrNull() ?: return@mapChapters null
			val href = a.attrAsRelativeUrl("href")
			val dateText = li.selectFirst("div.col-xs-4.text-center.small")?.text()
				?.replace("th&#225;ng", "tháng")?.replace("ng&#224;y", "ngày")

			MangaChapter(
				id = generateUid(href),
				title = a.text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(dateText),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	private val relativeTimePattern = Regex("(\\d+)\\s*(phút|giờ|ngày|tháng) trước")
	private val absoluteTimePattern = Regex("(\\d{2}-\\d{2}-\\d{4})")

	private fun parseChapterDate(dateText: String?): Long {
		if (dateText == null) return 0

		return when {
			dateText.contains("phút trước") -> {
				val match = relativeTimePattern.find(dateText)
				val minutes = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - minutes * 60 * 1000
			}

			dateText.contains("giờ trước") -> {
				val match = relativeTimePattern.find(dateText)
				val hours = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - hours * 3600 * 1000
			}

			dateText.contains("ngày trước") -> {
				val match = relativeTimePattern.find(dateText)
				val days = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - days * 86400 * 1000
			}

			dateText.contains("tháng trước") -> {
				val match = relativeTimePattern.find(dateText)
				val months = match?.groups?.get(1)?.value?.toIntOrNull() ?: 0
				System.currentTimeMillis() - months * 30 * 86400 * 1000
			}

			absoluteTimePattern.matches(dateText) -> {
				val formatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
				try {
					val parsedDate = formatter.parse(dateText)
					parsedDate?.time ?: 0L
				} catch (e: Exception) {
					0L
				}
			}

			else -> 0L
		}
	}

	private suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val tagItems = doc.select("ul.dropdown-menu.megamenu li a")
		val tagSet = ArraySet<MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.attr("title").toTitleCase(sourceLocale)
			val key = item.attr("href").substringAfterLast('/').trim()
			if (key.isNotEmpty() && title.isNotEmpty()) {
				tagSet.add(MangaTag(title = title, key = key, source = source))
			}
		}
		return tagSet
	}
}
