package org.koitharu.kotatsu.parsers.site.mangareader

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MangaReaderParser(
	source: MangaSource,
	pageSize: Int,
	searchPageSize: Int
) : PagedMangaParser(source, pageSize, searchPageSize) {

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	protected val idLocale
		get() = Locale("in", "ID")

	abstract val listUrl: String
	abstract val tableMode: Boolean
	open val chapterDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()
	private var lastSearchPage = 1

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml()
		val chapters = docs.select("#chapterlist > ul > li").mapChapters { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = chapterDateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source
			)
		}
		return if (tableMode) parseInfoTable(docs, manga, chapters) else parseInfoList(docs, manga, chapters)
	}

	open suspend fun parseInfoTable(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val mangaInfo = docs.selectFirst("div.seriestucontent > div.seriestucontentr")
		val mangaState = mangaInfo?.selectFirst(".infotable td:contains(Status)")?.lastElementSibling()?.let {
			when (it.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			}
		}
		val tagMap = getOrCreateTagMap()
		val tags = mangaInfo?.select(".seriestugenre > a")?.mapNotNullToSet { tagMap[it.text()] }

		return manga.copy(
			description = mangaInfo?.selectFirst("div.entry-content")?.html(),
			state = mangaState,
			author = mangaInfo?.selectFirst(".infotable td:contains(Author)")?.lastElementSibling()?.text(),
			isNsfw = docs.selectFirst(".restrictcontainer") != null,
			tags = tags.orEmpty(),
			chapters = chapters,
		)
	}

	open suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val mangaState = docs.selectFirst(".info-left .tsinfo div:contains(Status)")?.lastElementChild()?.let {
			when (it.text()) {
				"Ongoing" -> MangaState.ONGOING
				"Completed" -> MangaState.FINISHED
				else -> null
			}
		}
		val tagMap = getOrCreateTagMap()
		val tags = docs.select(".info-right .mgen > a").mapNotNullToSet { tagMap[it.text()] }

		return manga.copy(
			description = docs.selectFirst(".info-right div.entry-content > p")?.html(),
			state = mangaState,
			author = docs.selectFirst(".info-left .tsinfo div:contains(Author)")?.lastElementChild()?.text(),
			isNsfw = docs.selectFirst(".info-right .alr") != null,
			tags = tags,
			chapters = chapters,
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > lastSearchPage) {
				return emptyList()
			}

			val url = buildString {
				append("https://")
				append(getDomain())
				append("/page/")
				append(page)
				append("/?s=")
				append(query.urlEncoded())
			}

			val docs = context.httpGet(url).parseHtml()
			lastSearchPage = docs.selectFirst(".pagination .next")
				?.previousElementSibling()
				?.text()?.toIntOrNull() ?: 1
			return parseMangaList(docs)
		}

		val sortQuery = when (sortOrder) {
			SortOrder.ALPHABETICAL -> "title"
			SortOrder.NEWEST -> "latest"
			SortOrder.POPULARITY -> "popular"
			SortOrder.UPDATED -> "update"
			else -> ""
		}
		val tagKey = "genre[]".urlEncoded()
		val tagQuery = if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
		val url = buildString {
			append("https://")
			append(getDomain())
			append(listUrl)
			append("/?order=")
			append(sortQuery)
			append(tagQuery)
			append("&page=")
			append(page)
		}

		return parseMangaList(context.httpGet(url).parseHtml())
	}

	private fun parseMangaList(docs: Document): List<Manga> {
		return docs.select(".postbody .listupd .bs .bsx").mapNotNull {
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN

			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = a.attr("title"),
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				isNsfw = false,
				coverUrl = it.selectFirst("img.ts-post-image")?.imageUrl().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(getDomain())
		val docs = context.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow("script:containsData(ts_reader)")
		val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')'))
			.getJSONArray("sources")
			.getJSONObject(0)
			.getJSONArray("images")

		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			pages.add(
				MangaPage(
					id = generateUid(images.getString(i)),
					url = images.getString(i),
					referer = chapterUrl,
					preview = null,
					source = source
				)
			)
		}

		return pages
	}

	override suspend fun getTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()

		val url = listUrl.toAbsoluteUrl(getDomain())
		val tagElements = context.httpGet(url).parseHtml().select("ul.genrez > li")
		for (el in tagElements) {
			if (el.text().isEmpty()) continue

			tagMap[el.text()] = MangaTag(
				title = el.text(),
				key = el.selectFirst(".genre-item")?.attr("value") ?: continue,
				source = source
			)
		}

		return@withLock tagMap
	}

	private fun Element.imageUrl(): String {
		return attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-cfsrc")
			?: ""
	}

	@MangaSourceParser("MANHWALAND", "Manhwaland", "id")
	class ManhwaLandParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.MANHWALAND, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwaland.guru", null)

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = false
	}

	@MangaSourceParser("HEROXIA", "Heroxia", "id")
	class HeroxiaParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.HEROXIA, pageSize = 25, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("heroxia.com", null)

		override val tableMode: Boolean
			get() = true
		override val listUrl: String
			get() = "/manga"
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
	}


	@MangaSourceParser("SEKAIKOMIK", "Sekaikomik", "id")
	class SekaikomikParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.SEKAIKOMIK, pageSize = 20, searchPageSize = 100) {
		override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("sekaikomik.pro", null)

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM D, yyyy", idLocale)
	}

	@MangaSourceParser("MANHWAINDO", "Manhwaindo", "id")
	class ManhwaIndoParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.MANHWAINDO, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwaindo.id", null)

		override val listUrl: String get() = "/series"
		override val tableMode: Boolean get() = false
	}

	@MangaSourceParser("MANHWALIST", "Manhwalist", "id")
	class ManhwalistParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.MANHWALIST, pageSize = 24, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwalist.com", null)

		override val listUrl: String = "/manga"
		override val tableMode: Boolean get() = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KIRYUU", "Kiryuu", "id")
	class KiryuuParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.KIRYUU, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("kiryuu.id", null)

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", idLocale)
	}

	@MangaSourceParser("TURKTOON", "Turktoon", "tr")
	class TurktoonParser(override val context: MangaLoaderContext) : MangaReaderParser(MangaSource.TURKTOON, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("turktoon.com", null)

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr", "TR"))

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val mangaState = docs.selectFirst(".info-left .tsinfo div:contains(Durum)")?.lastElementChild()?.let {
				when (it.text()) {
					"Devam Ediyor" -> MangaState.ONGOING
					"Tamamlandı" -> MangaState.FINISHED
					else -> null
				}
			}

			return super.parseInfoList(docs, manga, chapters).copy(state = mangaState)
		}
	}
}