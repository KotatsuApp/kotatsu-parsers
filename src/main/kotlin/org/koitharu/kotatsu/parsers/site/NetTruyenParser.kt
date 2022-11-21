package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NETTRUYEN", "NetTruyen", "vi")
class NetTruyenParser(override val context: MangaLoaderContext) :
	PagedMangaParser(MangaSource.NETTRUYEN, pageSize = 36) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("nettruyenin.com", null)

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.RATING)

	private val mutex = Mutex()
	private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
	private var tagCache: ArrayMap<String, MangaTag>? = null

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml()
		val rating = doc.selectFirst("span[itemprop=ratingValue]")
			?.ownText()
			?.toFloatOrNull() ?: 0f

		val chapterElements = doc.getElementById("nt_listchapter")?.select("ul > li") ?: doc.parseFailed()
		val chapters = chapterElements.asReversed().mapChapters { index, element ->
			val a = element.selectFirst("div.chapter > a") ?: return@mapChapters null
			val relativeUrl = a.attrAsRelativeUrlOrNull("href") ?: return@mapChapters null
			val timeText = element.selectFirst("div.col-xs-4.text-center.no-wrap.small")?.text()

			MangaChapter(
				id = generateUid(relativeUrl),
				name = a.text(),
				number = index + 1,
				url = relativeUrl,
				scanlator = null,
				uploadDate = parseChapterTime(timeText),
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			rating = rating / 5,
			chapters = chapters,
			description = doc.selectFirst("div.detail-content > p")?.html(),
			isNsfw = doc.selectFirst("div.alert.alert-danger > strong:contains(Cảnh báo độ tuổi)") != null,
		)
	}

	// 20 giây trước
	// 52 phút trước
	// 6 giờ trước
	// 2 ngày trước
	// 19:09 30/07
	// 23/12/21
	private fun parseChapterTime(timeText: String?): Long {
		if (timeText.isNullOrEmpty()) {
			return 0L
		}

		val timeWords = arrayOf("giây", "phút", "giờ", "ngày")
		val calendar = Calendar.getInstance()
		val timeArr = timeText.split(' ')
		if (WordSet(*timeWords).anyWordIn(timeText)) {
			val timeSuffix = timeArr.getOrNull(1)
			val timeDiff = timeArr.getOrNull(0)?.toIntOrNull() ?: return 0L
			when (timeSuffix) {
				timeWords[0] -> calendar.add(Calendar.SECOND, -timeDiff)
				timeWords[1] -> calendar.add(Calendar.MINUTE, -timeDiff)
				timeWords[2] -> calendar.add(Calendar.HOUR, -timeDiff)
				timeWords[3] -> calendar.add(Calendar.DATE, -timeDiff)
				else -> return 0L
			}
		} else {
			val relativeDate = timeArr.lastOrNull() ?: return 0L
			val dateString = when (relativeDate.split('/').size) {
				2 -> {
					val currentYear = calendar.get(Calendar.YEAR).toString().takeLast(2)
					"$relativeDate/$currentYear"
				}
				3 -> relativeDate
				else -> return 0L
			}

			calendar.timeInMillis = dateFormat.tryParse(dateString)
		}


		return calendar.time.time
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val isSearching = !query.isNullOrEmpty()
		val url = buildString {
			append("https://")
			append(getDomain())
			if (isSearching) {
				append("/tim-truyen?keyword=")
				append(query!!.urlEncoded())
				append("&page=")
				append(page)
			} else {
				val tagQuery = tags.orEmpty().joinToString(",") { it.key }
				append("/tim-truyen-nang-cao?genres=$tagQuery")
				append("&notgenres=&gender=-1&status=-1&minchapter=1&sort=${getSortOrderKey(sortOrder)}")
				append("&page=$page")
			}
		}

		val response = if (isSearching) {
			val result = runCatching { context.httpGet(url) }
			val exception = result.exceptionOrNull()
			if (exception is NotFoundException) {
				return emptyList()
			}

			result.getOrThrow()
		} else {
			context.httpGet(url)
		}

		val itemsElements = response.parseHtml()
			.select("div.ModuleContent > div.items")
			.select("div.item")
		return itemsElements.mapNotNull { item ->
			val tooltipElement = item.selectFirst("div.box_tootip") ?: return@mapNotNull null
			val absUrl = item.selectFirst("div.image > a")?.attrAsAbsoluteUrlOrNull("href") ?: return@mapNotNull null
			val slug = absUrl.substringAfterLast('/')
			val mangaState = when (tooltipElement.selectFirst("div.message_main > p:contains(Tình trạng)")?.ownText()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Hoàn thành" -> MangaState.FINISHED
				else -> null
			}

			val tagMap = getOrCreateTagMap()
			val tagsElement = tooltipElement.selectFirst("div.message_main > p:contains(Thể loại)")?.ownText().orEmpty()
			val mangaTags = tagsElement.split(',').mapNotNullToSet { tagMap[it.trim()] }
			Manga(
				id = generateUid(slug),
				title = tooltipElement.selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				url = absUrl.toRelativeUrl(getDomain()),
				publicUrl = absUrl,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = item.selectFirst("div.image a img")?.absUrl("data-original").orEmpty(),
				largeCoverUrl = null,
				tags = mangaTags,
				state = mangaState,
				author = tooltipElement.selectFirst("div.message_main > p:contains(Tác giả)")?.ownText(),
				description = tooltipElement.selectFirst("div.box_text")?.text(),
				chapters = null,
				source = source,
			)
		}
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val pageElements = context.httpGet(chapter.url.toAbsoluteUrl(getDomain())).parseHtml()
			.select("div.reading-detail.box_doc > div img")
		return pageElements.map { element ->
			val url = element.attrAsAbsoluteUrl("data-original")
			MangaPage(
				id = generateUid(url),
				url = url,
				referer = getDomain(),
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val map = getOrCreateTagMap()
		val tagSet = ArraySet<MangaTag>(map.size)
		for (entry in map) {
			tagSet.add(entry.value)
		}

		return tagSet
	}

	private suspend fun getOrCreateTagMap(): ArrayMap<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val doc = context.httpGet("/tim-truyen-nang-cao".toAbsoluteUrl(getDomain())).parseHtml()
		val tagItems = doc.select("div.genre-item")
		val result = ArrayMap<String, MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.text().trim()
			val key = item.select("span[data-id]").attr("data-id")
			result[title] = MangaTag(title = title, key = key, source = source)
		}
		tagCache = result
		result
	}

	private fun getSortOrderKey(sortOrder: SortOrder) = when (sortOrder) {
		SortOrder.UPDATED -> 0
		SortOrder.POPULARITY -> 10
		SortOrder.NEWEST -> 15
		SortOrder.RATING -> 20
		else -> throw IllegalArgumentException("Sort order ${sortOrder.name} not supported")
	}
}
