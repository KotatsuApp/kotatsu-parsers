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
	context: MangaLoaderContext,
	source: MangaSource,
	pageSize: Int,
	searchPageSize: Int,
) : PagedMangaParser(context, source, pageSize, searchPageSize) {

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	protected val idLocale
		get() = Locale("in", "ID")

	abstract val listUrl: String
	abstract val tableMode: Boolean
	protected open val isNsfwSource = false
	open val chapterDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()
	private var lastSearchPage = 1

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapters = docs.select("#chapterlist > ul > li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = chapterDateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source,
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
			isNsfw = manga.isNsfw || docs.selectFirst(".restrictcontainer") != null,
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
			isNsfw = manga.isNsfw || docs.selectFirst(".info-right .alr") != null,
			tags = tags,
			chapters = chapters,
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > lastSearchPage) {
				return emptyList()
			}

			val url = buildString {
				append("https://")
				append(domain)
				append("/page/")
				append(page)
				append("/?s=")
				append(query.urlEncoded())
			}

			val docs = webClient.httpGet(url).parseHtml()
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
		val tagQuery =
			if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("/?order=")
			append(sortQuery)
			append(tagQuery)
			append("&page=")
			append(page)
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
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
					preview = null,
					source = source,
				),
			)
		}

		return pages
	}

	override suspend fun getTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	protected suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()

		val url = listUrl.toAbsoluteUrl(domain)
		val tagElements = webClient.httpGet(url).parseHtml().select("ul.genrez > li")
		for (el in tagElements) {
			if (el.text().isEmpty()) continue

			tagMap[el.text()] = MangaTag(
				title = el.text(),
				key = el.selectFirst("input")?.attr("value") ?: continue,
				source = source,
			)
		}

		tagCache = tagMap
		return@withLock tagMap
	}

	private fun Element.imageUrl(): String {
		return attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-src")
			?: attrAsAbsoluteUrlOrNull("data-cfsrc")
			?: ""
	}

	@MangaSourceParser("MANHWALAND", "Manhwaland", "id")
	class ManhwaLandParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWALAND, pageSize = 20, searchPageSize = 10) {

		override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manhwaland.us", "manhwaland.guru")
		override val listUrl: String = "/manga"
		override val tableMode: Boolean = false
	}

	@MangaSourceParser("SEKAIKOMIK", "Sekaikomik", "id")
	class SekaikomikParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SEKAIKOMIK, pageSize = 20, searchPageSize = 100) {
		override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("sekaikomik.pro")

		override val listUrl: String = "/manga"
		override val tableMode: Boolean = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM D, yyyy", idLocale)
	}

	@MangaSourceParser("MANHWAINDO", "Manhwaindo", "id")
	class ManhwaIndoParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWAINDO, pageSize = 30, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwaindo.id")

		override val chapterDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
		override val listUrl: String get() = "/series"
		override val tableMode: Boolean get() = false
	}

	@MangaSourceParser("MANHWALIST", "Manhwalist", "id")
	class ManhwalistParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWALIST, pageSize = 24, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwalist.in")

		override val listUrl: String = "/manga"
		override val tableMode: Boolean get() = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KIRYUU", "Kiryuu", "id")
	class KiryuuParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KIRYUU, pageSize = 30, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("kiryuu.id")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", idLocale)
	}

	@MangaSourceParser("TURKTOON", "Turktoon", "tr")
	class TurktoonParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TURKTOON, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("turktoon.com")

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

	@MangaSourceParser("WESTMANGA", "Westmanga", "id")
	class WestmangaParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.WESTMANGA, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("westmanga.info")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("TEMPESTFANSUB", "Tempestfansub", "tr")
	class TempestfansubParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TEMPESTFANSUB, pageSize = 25, searchPageSize = 40) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("tempestscans.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = true
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr", "TR"))

		override suspend fun parseInfoTable(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Artist)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("MANHWADESU", "ManhwaDesu", "id")
	class ManhwadesuParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWADESU, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwadesu.pro", "manhwadesu.org")

		override val listUrl: String get() = "/komik"
		override val tableMode: Boolean get() = false
	}

	@MangaSourceParser("MANGATALE", "MangaTale", "id")
	class MangaTaleParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGATALE, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangatale.co")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("DRAGONTRANSLATION", "DragonTranslation", "es")
	class DragonTranslationParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.DRAGONTRANSLATION, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("dragontranslation.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("ASURATR", "Asura Scans (tr)", "tr")
	class AsuraTRParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ASURATR, pageSize = 30, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("asurascanstr.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr"))

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Yazar)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("KOMIKTAP", "KomikTap", "id")
	class KomikTapParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKTAP, pageSize = 25, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("92.87.6.124", "komiktap.in")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)
	}

	@MangaSourceParser("KUMAPOI", "KumaPoi", "id")
	class KumaPoiParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KUMAPOI, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("kumapoi.me")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("ASURASCANS", "Asura Scans", "en")
	class AsuraScansParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ASURASCANS, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("asurascans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("TOONHUNTER", "Toon Hunter", "th")
	class ToonHunterParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TOONHUNTER, pageSize = 30, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("toonhunter.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", sourceLocale)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				isNsfw = manga.isNsfw || docs.selectFirst(".postbody .alr") != null,
			)
		}
	}

	@MangaSourceParser("COSMICSCANS", "CosmicScans", "en")
	class CosmicScansParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.COSMICSCANS, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("cosmicscans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
			)
		}
	}

	@MangaSourceParser("PHENIXSCANS", "Phenixscans", "fr")
    class PhenixscansParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.PHENIXSCANS, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("phenixscans.fr")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.FRENCH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Auteur)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
			)
		}
	}	
	
	@MangaSourceParser("KOMIKLOKAL", "KomikLokal", "id")
	class KomikLokalParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKLOKAL, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikmirror.art")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
			)
		}
	}

	@MangaSourceParser("KOMIKAV", "KomiKav", "id")
	class KomiKavParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKAV, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikav.com")

		override val listUrl: String = "/manga"
		override val tableMode: Boolean = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
				author = infoElement?.selectFirst(".flex-wrap div:contains(Author)")?.lastElementSibling()?.text(),
				tags = infoElement?.select(".wd-full .mgen > a")
					?.mapNotNullToSet { getOrCreateTagMap()[it.text()] }
					.orEmpty(),
			)
		}
	}

	@MangaSourceParser("KOMIKDEWASA", "KomikDewasa", "id")
	class KomikDewasaParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKDEWASA, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikdewasa.us", "komikdewasa.info")

		override val listUrl: String = "/manga"
		override val tableMode: Boolean = false
		override val isNsfwSource: Boolean = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

		override suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
			val infoElement = docs.selectFirst("div.infox")
			return manga.copy(
				chapters = chapters,
				description = infoElement?.selectFirst("div.entry-content")?.html(),
			)
		}
	}

	@MangaSourceParser("MANGASUSU", "Mangasusu", "id")
	class MangasusuParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGASUSU, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangasusu.co.in")

		override val listUrl: String
			get() = "/project"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KOMIKLAB", "KomikLab", "id")
	class KomikLabParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKLAB, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komiklab.com")

		override val listUrl: String
			get() = "/project"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KOMIKINDO", "KomikIndo", "id")
	class KomikIndoParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKINDO, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikindo.co")

		override val listUrl: String
			get() = "/project"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)
	}

	@MangaSourceParser("KOMIKMANGA", "KomikManga", "id")
	class KomikMangaParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKMANGA, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikhentai.co")

		override val listUrl: String
			get() = "/project"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}
}
