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
		val mangaInfo =
			docs.selectFirst("div.seriestucontent > div.seriestucontentr") ?:
			docs.selectFirst("div.seriestucontentr") ?:
			docs.selectFirst("div.seriestucon")

		val mangaState = mangaInfo?.selectFirst(".infotable td:contains(Status)")?.lastElementSibling()?.let {
			when (it.text()) {
				"Ongoing",
				"Devam Ediyor"
				-> MangaState.ONGOING
				"Completed",
				"Tamamlandı"
				-> MangaState.FINISHED
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

		val state_select =
			docs.selectFirst(".tsinfo div:contains(Status)") ?:
			docs.selectFirst(".tsinfo div:contains(Durum)")

		val mangaState = state_select?.lastElementChild()?.let {
			when (it.text()) {
				"Ongoing",
				"Devam Ediyor"
				-> MangaState.ONGOING
				"Completed",
				"Tamamlandı"
				-> MangaState.FINISHED
				else -> null
			}
		}

		val tags = docs.select(".wd-full .mgen > a").mapNotNullToSet { getOrCreateTagMap()[it.text()] }

		return manga.copy(
			description =
			docs.selectFirst("div.entry-content")?.html(),

			state = mangaState,
			author =
			docs.selectFirst(".tsinfo div:contains(Author)")?.lastElementChild()?.text() ?:
			docs.selectFirst(".tsinfo div:contains(Auteur)")?.lastElementChild()?.text() ?:
			docs.selectFirst(".tsinfo div:contains(Artist)")?.lastElementChild()?.text() ?:
			docs.selectFirst(".tsinfo div:contains(Durum)")?.lastElementChild()?.text() ,

			isNsfw = manga.isNsfw
					|| docs.selectFirst(".info-right .alr") != null
					|| docs.selectFirst(".postbody .alr") != null,
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


	/*
	laguage :
	ar
	ja
	tr
	es
	en
	th
	fr
	id
	pt
	it
	 */

	// Ar site //

	@MangaSourceParser("OZULSCANS", "Ozulscans", "ar")
	class Ozulscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.OZULSCANS, pageSize = 30, searchPageSize = 30) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("ozulscans.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy,d MMM", Locale("ar", "AR"))
	}

	// Ja site //


	@MangaSourceParser("RAWKUMA", "Rawkuma", "ja")
	class Rawkuma(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.RAWKUMA, pageSize = 54, searchPageSize = 54) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("rawkuma.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	// Tr site //

	@MangaSourceParser("MANGACIM", "Mangacim", "tr")
	class Mangacim(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGACIM, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangacim.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr"))
	}

	@MangaSourceParser("ASURATR", "Asura Scans (tr)", "tr")
	class AsuraTRParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ASURATR, pageSize = 30, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("asurascanstr.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr"))
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

	}

	@MangaSourceParser("TEMPESTFANSUB", "Tempestfansub", "tr")
	class TempestfansubParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TEMPESTFANSUB, pageSize = 25, searchPageSize = 40) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manga.tempestfansub.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false
		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

	}


	// Es site //

	@MangaSourceParser("SHADOWMANGAS", "Shadowmangas", "es")
	class Shadowmangas(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SHADOWMANGAS, pageSize = 10, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("shadowmangas.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es", "ES"))
	}


	@MangaSourceParser("SENPAIEDICIONES", "Senpaiediciones", "es")
	class Senpaiediciones(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SENPAIEDICIONES, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("senpaiediciones.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es", "ES"))
	}

	@MangaSourceParser("RAIKISCAN", "Raikiscan", "es")
	class Raikiscan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.RAIKISCAN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("raikiscan.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es", "ES"))
	}

	@MangaSourceParser("CARTELDEMANHWAS", "Cartel De Manhwas", "es")
	class CartelDeManhwas(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.CARTELDEMANHWAS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("carteldemanhwas.com")

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("es", "ES"))
	}

	@MangaSourceParser("DRAGONTRANSLATION", "DragonTranslation", "es")
	class DragonTranslationParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.DRAGONTRANSLATION, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("dragontranslation.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

	}

	@MangaSourceParser("MIAUSCAN", "Miau Scan", "es")
	class MiauScan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MIAUSCAN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("miauscan.com")

		override val listUrl: String get() = "/manga"
		override val tableMode: Boolean get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es", "ES"))

	}

	@MangaSourceParser("GREMORYMANGAS", "Gremory Mangas", "es")
	class GREMORYMANGAS(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.GREMORYMANGAS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("gremorymangas.com")

		override val listUrl: String get() = "/manga"

		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es", "ES"))
	}

	// En site //


	@MangaSourceParser("READKOMIK", "Readkomik", "en")
	class Readkomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.READKOMIK, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("readkomik.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("NIGHTSCANS", "Nightscans", "en")
	class Nightscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.NIGHTSCANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("nightscans.org")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}


	@MangaSourceParser("PHANTOMSCANS", "Phantomscans", "en")
	class Phantomscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.PHANTOMSCANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("phantomscans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("SURYASCANS", "Suryascans", "en")
	class Suryascans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SURYASCANS, pageSize = 5, searchPageSize = 5) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("suryascans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}


	@MangaSourceParser("RAVENSCANS", "Ravenscans", "en")
	class Ravenscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.RAVENSCANS, pageSize = 10, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("ravenscans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("MANHWALOVER", "ManhwaLover", "en")
	class ManhwaLover(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWALOVER, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwalover.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource: Boolean = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("MANHWAX", "Manhwax", "en")
	class Manhwax(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANHWAX, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("manhwax.org")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource: Boolean = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
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
	}

	@MangaSourceParser("ARENASCANS", "Arena Scans", "en")
	class ArenaScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ARENASCANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("arenascans.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

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

	}

	@MangaSourceParser("FLAMESCANS", "Flame Scans", "en")
	class FlameScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.FLAMESCANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("flamescans.org")

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("ANIGLISCANS", "Anigli Scans", "en")
	class AnigliScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ANIGLISCANS, pageSize = 47, searchPageSize = 47) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("anigliscans.com")

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("AZUREMANGA", "Azure Manga", "en")
	class AzureManga(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.AZUREMANGA, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("azuremanga.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("ELARCPAGE", "Elarcpage", "en")
	class Elarcpage(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ELARCPAGE, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("elarcpage.com")

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("VOIDSCANS", "Void Scans", "en")
	class VoidScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.VOIDSCANS, pageSize = 150, searchPageSize = 150) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("void-scans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KUMASCANS", "Kuma Scans", "en")
	class KumaScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KUMASCANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("kumascans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}


	// Th Site //

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
	}


	// Fr site //

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

	}

	@MangaSourceParser("SUSHISCAN", "SushiScan", "fr")
	class SushiScan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SUSHISCAN, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("sushiscan.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.FRENCH)
	}

	@MangaSourceParser("BANANASCAN", "Banana Scan", "fr")
	class BananaScan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.BANANASCAN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("banana-scan.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.FRENCH)

	}

	@MangaSourceParser("EPSILONSCAN", "Epsilonscan", "fr")
    class EpsilonscanParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.EPSILONSCAN, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("epsilonscan.fr")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false
        override val isNsfwSource: Boolean = true    

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
	}

	@MangaSourceParser("LEGACY_SCANS", "Legacy Scans", "fr")
    class LegacyScansParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.LEGACY_SCANS, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("legacy-scans.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false  

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
	}


	// Id site //


	@MangaSourceParser("KOMIKLOKAL", "Komik Lokal", "id")
	class KomikLokalParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKLOKAL, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikmirror.art")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KOMIKAV", "Komik Av", "id")
	class KomikAvParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKAV, pageSize = 20, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikav.com")

		override val listUrl: String = "/manga"
		override val tableMode: Boolean = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KOMIKDEWASA", "KomikDewasa", "id")
	class KomikDewasaParser(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKDEWASA, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikdewasa.cfd")

		override val listUrl: String = "/komik"
		override val tableMode: Boolean = true
		override val isNsfwSource: Boolean = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
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
			get() = false

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

	@MangaSourceParser("SOULSCANS", "Soul Scans", "id")
	class SoulScans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SOULSCANS, pageSize = 30, searchPageSize = 30) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("soulscans.my.id")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("BOOSEI", "Boosei", "id")
	class Boosei(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.BOOSEI, pageSize = 30, searchPageSize = 30) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("boosei.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)
	}

	@MangaSourceParser("DOJING", "Dojing", "id")
	class Dojing(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.DOJING, pageSize = 12, searchPageSize = 12) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("dojing.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("DUNIAKOMIK", "Duniakomik", "id")
	class Duniakomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.DUNIAKOMIK, pageSize = 12, searchPageSize = 12) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("duniakomik.id")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("KATAKOMIK", "Katakomik", "id")
	class Katakomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KATAKOMIK, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("katakomik.online")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KANZENIN", "Kanzenin", "id")
	class Kanzenin(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KANZENIN, pageSize = 25, searchPageSize = 25) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("kanzenin.xyz")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}

	@MangaSourceParser("KOMIKSTATION", "Komikstation", "id")
	class Komikstation(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKSTATION, pageSize = 30, searchPageSize = 30) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikstation.co")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
	}


	@MangaSourceParser("KOMIKMAMA", "Komik Mama", "id")
	class KomikMama(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKMAMA, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikmama.co")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}

	@MangaSourceParser("KOMIKMANHWA", "Komik Manhwa", "id")
	class KomikManhwa(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKMANHWA, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komikmanhwa.me")

		override val listUrl: String
			get() = "/series"
		override val tableMode: Boolean
			get() = true

		override val isNsfwSource = true


		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("KOMIKU", "Komiku", "id")
	class Komiku(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.KOMIKU, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("komiku.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}


	@MangaSourceParser("MANGAYARO", "Mangayaro", "id")
	class Mangayaro(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGAYARO, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangayaro.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}

	@MangaSourceParser("MANGAKITA", "MangaKita", "id")
	class MangakKita(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGAKITA, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangakita.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}


	@MangaSourceParser("MANGASUSUKU", "MangaSusuku", "id")
	class MangaSusuku(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGASUSUKU, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangasusuku.xyz")

		override val listUrl: String
			get() = "/komik"
		override val tableMode: Boolean
			get() = true

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}

	@MangaSourceParser("MASTERKOMIK", "MasterKomik", "id")
	class MasterKomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MASTERKOMIK, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("masterkomik.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}


	@MangaSourceParser("MELOKOMIK", "Melokomik", "id")
	class Melokomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MELOKOMIK, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("melokomik.xyz")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("MIRRORDESU", "Mirrordesu", "id")
	class Mirrordesu(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MIRRORDESU, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mirrordesu.ink")

		override val listUrl: String
			get() = "/komik"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("LIANSCANS", "Lianscans", "id")
	class Lianscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.LIANSCANS, pageSize = 10, searchPageSize = 10) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("www.lianscans.my.id")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("MANGAINDO", "Mangaindo", "id")
	class Mangaindo(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGAINDO, pageSize = 26, searchPageSize = 26) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangaindo.me")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}


	@MangaSourceParser("PISCANS", "Piscans", "id")
	class Piscans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.PISCANS, pageSize = 24, searchPageSize = 24) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("piscans.in")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}

	@MangaSourceParser("SEKTEDOUJIN", "Sektedoujin", "id")
	class Sektedoujin(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SEKTEDOUJIN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("sektedoujin.cc")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}

	@MangaSourceParser("SHEAKOMIK", "Sheakomik", "id")
	class Sheakomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SHEAKOMIK, pageSize = 40, searchPageSize = 40) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("sheakomik.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	}


	@MangaSourceParser("TUKANGKOMIK", "Tukangkomik", "id")
	class Tukangkomik(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TUKANGKOMIK, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("tukangkomik.id")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

	}

	// Pt Site //

	@MangaSourceParser("FRANXXMANGAS", "Franxx Mangas", "pt")
	class FranxxMangas(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.FRANXXMANGAS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("franxxmangas.net")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	@MangaSourceParser("MANGASCHAN", "Mangaschan", "pt")
	class Mangaschan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MANGASCHAN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mangaschan.com")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	@MangaSourceParser("SILENCESCAN", "Silencescan", "pt")
	class Silencescan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.SILENCESCAN, pageSize = 35, searchPageSize = 35) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("silencescan.com.br")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val isNsfwSource = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	@MangaSourceParser("TSUNDOKU", "Tsundoku", "pt")
	class Tsundoku(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.TSUNDOKU, pageSize = 50, searchPageSize = 50) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("tsundoku.com.br")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	@MangaSourceParser("ORIGAMIORPHEANS", "Origami orpheans", "pt")
	class Origamiorpheans(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.ORIGAMIORPHEANS, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("origami-orpheans.com.br")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = true

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	@MangaSourceParser("MUNDOMANGAKUN", "Mundomangakun", "pt")
	class Mundomangakun(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.MUNDOMANGAKUN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("mundomangakun.com.br")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

	}

	// It Site //

	@MangaSourceParser("WALPURGISCAN", "Walpurgiscan", "it")
	class Walpurgiscan(context: MangaLoaderContext) :
		MangaReaderParser(context, MangaSource.WALPURGISCAN, pageSize = 20, searchPageSize = 20) {
		override val configKeyDomain: ConfigKey.Domain
			get() = ConfigKey.Domain("walpurgiscan.it")

		override val listUrl: String
			get() = "/manga"
		override val tableMode: Boolean
			get() = false

		override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("it", "IT"))

	}
}
