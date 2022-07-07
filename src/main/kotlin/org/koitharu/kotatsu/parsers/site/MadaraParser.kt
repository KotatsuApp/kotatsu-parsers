package org.koitharu.kotatsu.parsers.site

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 12

internal abstract class MadaraParser(
	override val context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
) : MangaParser(source) {

	override val configKeyDomain = ConfigKey.Domain(domain, null)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	protected open val tagPrefix = "manga-genre/"
	protected open val isNsfwSource = false

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val payload = createRequestTemplate()
		payload["page"] = (offset / PAGE_SIZE.toFloat()).toIntUp().toString()
		payload["vars[meta_key]"] = when (sortOrder) {
			SortOrder.POPULARITY -> "_wp_manga_views"
			SortOrder.UPDATED -> "_latest_update"
			else -> "_wp_manga_views"
		}
		payload["vars[wp-manga-genre]"] = tag?.key.orEmpty()
		payload["vars[s]"] = query.orEmpty()
		val doc = context.httpPost(
			"https://${getDomain()}/wp-admin/admin-ajax.php",
			payload,
		).parseHtml()
		return doc.select("div.row.c-tabs-item__content").map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
				?: parseFailed("Link not found")
			val summary = div.selectFirst(".tab-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: getDomain()),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()
					?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
						title = a.text().toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (
					summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")
						?.ownText()?.trim()?.lowercase()
				) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}/manga/").parseHtml()
		val body = doc.body()
		val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
		val root2 = body.selectFirst("div.genres_wrap")?.selectFirst("ul.list-unstyled")
		if (root1 == null && root2 == null) {
			parseFailed("Root not found")
		}
		val list = root1?.select("li").orEmpty() + root2?.select("li").orEmpty()
		val keySet = HashSet<String>(list.size)
		return list.mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix("/")
				.substringAfterLast(tagPrefix, "")
			if (href.isEmpty() || !keySet.add(href)) {
				return@mapNotNullToSet null
			}
			MangaTag(
				key = href,
				title = a.ownText().trim().ifEmpty {
					a.selectFirst(".menu-image-title")?.text()?.trim() ?: return@mapNotNullToSet null
				}.toTitleCase(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.profile-manga")
			?.selectFirst("div.summary_content")
			?.selectFirst("div.post-content")
			?: throw ParseException("Root not found")
		val root2 = doc.body().selectFirst("div.content-area")
			?.selectFirst("div.c-page")
			?: throw ParseException("Root2 not found")
		val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
		return manga.copy(
			tags = root.selectFirst("div.genres-content")?.select("a")
				?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
						title = a.text().toTitleCase(),
						source = source,
					)
				} ?: manga.tags,
			description = root2.selectFirst("div.description-summary")
				?.selectFirst("div.summary__content")
				?.select("p")
				?.filterNot { it.ownText().startsWith("A brief description") }
				?.joinToString { it.html() },
			chapters = root2.select("li").asReversed().mapIndexed { i, li ->
				val a = li.selectFirst("a")
				val href = a?.attrAsRelativeUrlOrNull("href") ?: parseFailed("Link is missing")
				MangaChapter(
					id = generateUid(href),
					name = a.ownText(),
					number = i + 1,
					url = href,
					uploadDate = parseChapterDate(
						dateFormat,
						li.selectFirst("span.chapter-release-date i")?.text(),
					),
					source = source,
					scanlator = null,
					branch = null,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.main-col-inner")
			?.selectFirst("div.reading-content")
			?: throw ParseException("Root not found")
		return root.select("div.page-break").map { div ->
			val img = div.selectFirst("img") ?: parseFailed("Page image not found")
			val url = img.src()?.toRelativeUrl(getDomain()) ?: parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				referer = fullUrl,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0
		return when {
			date.endsWith(" ago", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in Portuguese.
			date.endsWith(" atrás", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in Turkish.
			date.endsWith(" önce", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle 'yesterday' and 'today', using midnight
			date.startsWith("year", ignoreCase = true) -> {
				Calendar.getInstance().apply {
					add(Calendar.DAY_OF_MONTH, -1) // yesterday
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}
			date.startsWith("today", ignoreCase = true) -> {
				Calendar.getInstance().apply {
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}
			date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
				// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
				date.split(" ").map {
					if (it.contains(Regex("""\d\D\D"""))) {
						it.replace(Regex("""\D"""), "")
					} else {
						it
					}
				}
					.let { dateFormat.tryParse(it.joinToString(" ")) }
			}
			else -> dateFormat.tryParse(date)
		}
	}

	// Parses dates in this form:
	// 21 hours ago
	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"hari",
				"gün",
				"jour",
				"día",
				"dia",
				"day",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("jam", "saat", "heure", "hora", "hour").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis
			WordSet("menit", "dakika", "min", "minute", "minuto").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis
			WordSet("detik", "segundo", "second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis
			WordSet("month").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	private fun Element.src(): String? {
		return absUrl("data-src").ifEmpty {
			absUrl("src")
		}.takeUnless { it.isEmpty() }
	}

	private fun createRequestTemplate() =
		(
			"action=madara_load_more&page=1&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5B" +
				"orderby%5D=meta_value_num&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query" +
				"%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5Brelation%5D=OR&vars%5Bpost_type" +
				"%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmeta_key%5D=_latest_update&vars%5Border" +
				"%5D=desc&vars%5Bmanga_archives_item_layout%5D=default"
			).split('&')
			.map {
				val pos = it.indexOf('=')
				it.substring(0, pos) to it.substring(pos + 1)
			}.toMutableMap()

	@MangaSourceParser("MANGAREAD", "MangaRead", "en")
	class MangaRead(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAREAD, "www.mangaread.org") {
		override val tagPrefix = "genres/"
	}

	@MangaSourceParser("REAPERSCANS", "ReaperScans", "en")
	class ReaperScans(context: MangaLoaderContext) : MadaraParser(context, MangaSource.REAPERSCANS, "reaperscans.com")

	@MangaSourceParser("MANGAWEEBS", "MangaWeebs", "en")
	class MangaWeebs(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAWEEBS, "mangaweebs.in")

	@MangaSourceParser("KINGMANGA", "KingManga", "en")
	class KingManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.KINGMANGA, "king-manga.com")

	@MangaSourceParser("MANGAHATACHI", "MangahaTachi", "ja")
	class MangahaTachi(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAHATACHI, "mangahatachi.com")

	@MangaSourceParser("PIANMANGA", "PianManga", "en")
	class PianManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.PIANMANGA, "pianmanga.com")

	@MangaSourceParser("MANGAROSIE", "MangaRosie", "en")
	class MangaRosie(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAROSIE, "mangarosie.me")

	@MangaSourceParser("READMANWHA", "ReadManwha", "en")
	class ReadManwha(context: MangaLoaderContext) : MadaraParser(context, MangaSource.READMANWHA, "readmanwha.net")

	@MangaSourceParser("MANGATX", "MangaTx", "en")
	class MangaTx(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGATX, "mangatx.com")

	@MangaSourceParser("MANGAEFFECT", "MangaEffect", "en")
	class MangaEffect(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAEFFECT, "mangaeffect.com")

	@MangaSourceParser("AQUAMANGA", "AquaManga", "en")
	class AquaManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.AQUAMANGA, "aquamanga.com") {

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2021/03/cropped-cropped-favicon-1-32x32.png"
		}
	}

	@MangaSourceParser("MANGATX_OT", "MangaTx (ot)", "en")
	class MangaTxOt(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGATX_OT, "manga-tx.com")

	@MangaSourceParser("MANGALEK", "MangaLek", "ar")
	class MangaLek(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGALEK, "mangalek.com")

	@MangaSourceParser("HARIMANGA", "HariManga", "en")
	class HariManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.HARIMANGA, "harimanga.com")

	@MangaSourceParser("KISSMANGA", "KissManga", "en")
	class KissManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.KISSMANGA, "kissmanga.in")

	@MangaSourceParser("COFFEEMANGA", "CoffeeManga", "en")
	class CoffeeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.COFFEEMANGA, "coffeemanga.com")

	@MangaSourceParser("MANGAROCK", "MangaRock", "en")
	class MangaRock(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAROCK, "mangarockteam.com")

	@MangaSourceParser("ISEKAISCAN_EU", "IsekaiScan (eu)", "en")
	class IsekaiScanEu(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ISEKAISCAN_EU, "isekaiscan.eu")

	@MangaSourceParser("FREEMANGA", "FreeManga", "en")
	class FreeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.FREEMANGA, "freemanga.me")

	@MangaSourceParser("ISEKAISCAN", "IsekaiScan", "en")
	class IsekaiScan(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ISEKAISCAN, "isekaiscan.com") {

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2021/10/isekai-scan-02-01-150x150.png"
		}
	}

	@MangaSourceParser("MANGA_KOMI", "MangaKomi", "en")
	class MangaKomi(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_KOMI, "mangakomi.io")

	@MangaSourceParser("READMANHWA", "ReadManhwa", "en")
	class ReadManhwa(context: MangaLoaderContext) : MadaraParser(context, MangaSource.READMANHWA, "readmanhwa.info")

	@MangaSourceParser("NEATMANGA", "NeatManga", "en")
	class NeatManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.NEATMANGA, "neatmanga.com")

	@MangaSourceParser("MANGAKIK", "MangaKik", "en")
	class MangaKik(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGAKIK, "mangakik.net")

	@MangaSourceParser("MANHWACLAN", "ManhwaClan", "en")
	class ManhwaClan(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANHWACLAN, "manhwaclan.com")

	@MangaSourceParser("MANGA_3S", "Manga3s", "en")
	class Manga3s(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_3S, "manga3s.com")

	@MangaSourceParser("MANGATITANS", "MangaTitans", "th")
	class MangaTitans(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGATITANS, "manga-titans.com")

	@MangaSourceParser("MANHWAKOOL", "Manhwa Kool", "en")
	class ManhwaKool(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANHWAKOOL, "manhwakool.com") {
		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2021/10/cropped-logo-kool-32x32.jpeg"
		}
	}

	@MangaSourceParser("TOPMANHUA", "Top Manhua", "en")
	class TopManhua(context: MangaLoaderContext) : MadaraParser(context, MangaSource.TOPMANHUA, "www.topmanhua.com") {
		override val tagPrefix = "manhua-genre/"
	}

	@MangaSourceParser("X2MANGA", "X2Manga", "en")
	class X2Manga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.X2MANGA, "x2manga.com")

	@MangaSourceParser("VINLOAD", "VinLoad", "en")
	class VinLoad(context: MangaLoaderContext) : MadaraParser(context, MangaSource.VINLOAD, "vinload.com")

	@MangaSourceParser("S2MANGA", "S2Manga", "en")
	class S2Manga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.S2MANGA, "s2manga.com")

	@MangaSourceParser("MANHWA", "Manhwa", "en")
	class Manhwa(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANHWA, "manhwa.pics")

	@MangaSourceParser("MANGASCO", "MangaSco", "en")
	class MangaSco(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGASCO, "mangasco.com")

	@MangaSourceParser("SKY_MANGA", "Sky Manga", "en")
	class SkyManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.SKY_MANGA, "skymanga.xyz") {

		override val isNsfwSource = true

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/cropped-sky-tv-1-32x32.png"
		}
	}

	@MangaSourceParser("MANGA_DISTRICT", "Manga District", "en")
	class MangaDistrict(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_DISTRICT, "mangadistrict.com") {

		override val tagPrefix = "publication-genre/"

		override val isNsfwSource = true
	}

	@MangaSourceParser("HENTAI_4FREE", "Hentai4Free", "en")
	class Hentai4Free(context: MangaLoaderContext) : MadaraParser(context, MangaSource.HENTAI_4FREE, "hentai4free.net") {

		override val tagPrefix = "hentai-tag/"

		override val isNsfwSource = true

		override suspend fun getTags(): Set<MangaTag> {
			val doc = context.httpGet("https://${getDomain()}/").parseHtml()
			val body = doc.body()
			val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
			val list = root1?.select("li").orEmpty()
			val keySet = HashSet<String>(list.size)
			return list.mapNotNullToSet { li ->
				val a = li.selectFirst("a") ?: return@mapNotNullToSet null
				val href = a.attr("href").removeSuffix("/")
					.substringAfterLast(tagPrefix, "")
				if (href.isEmpty() || !keySet.add(href)) {
					return@mapNotNullToSet null
				}
				MangaTag(
					key = href,
					title = a.ownText().trim().toTitleCase(),
					source = source,
				)
			}
		}
	}

	@MangaSourceParser("ALLPORN_COMIC", "All Porn Comic", "en")
	class AllPornComic(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ALLPORN_COMIC, "allporncomic.com") {

		override val tagPrefix = "porncomic-genre/"

		override val isNsfwSource = true

		override fun getFaviconUrl(): String {
			return "https://cdn.${getDomain()}/wp-content/uploads/2019/01/cropped-cropped-pcround-32x32.png"
		}
	}

	@MangaSourceParser("CAT_300", "Cat300", "th")
	class Cat300(context: MangaLoaderContext) : MadaraParser(context, MangaSource.CAT_300, "cat300.com") {

		override val isNsfwSource = true
	}

	@MangaSourceParser("BIBIMANGA", "BibiManga", "en")
	class BibiManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.BIBIMANGA, "bibimanga.com") {

		override val isNsfwSource = true
	}

	@MangaSourceParser("MANHWA_CHILL", "Manhwa Chill", "en")
	class ManhwaChill(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANHWA_CHILL, "manhwachill.me")

	@MangaSourceParser("TREE_MANGA", "Tree Manga", "en")
	class TreeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.TREE_MANGA, "treemanga.com") {

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2017/10/lgoo-treemanga-2-1.jpg"
		}
	}

	@MangaSourceParser("ALLTOPMANGA", "All Top Manga", "en")
	class AllTopManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ALLTOPMANGA, "alltopmanga.com") {

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2021/12/cropped-Screenshot_4-removebg-preview-32x32.png"
		}
	}

	@MangaSourceParser("MANGACV", "Manga Cv", "en")
	class MangaCv(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGACV, "mangacv.com") {

		override fun getFaviconUrl(): String {
			return "https://${getDomain()}/wp-content/uploads/2020/10/cropped-mangaCV-32x32.png"
		}
	}

	@MangaSourceParser("MANGA_MANHUA", "Manga Manhua", "en")
	class MangaManhua(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_MANHUA, "mangamanhua.online")

        @MangaSourceParser("DOKIMORI", "DokiMori", "th")
	class DokiMori(context: MangaLoaderContext) : MadaraParser(context, MangaSource.DOKIMORI, "www.dokimori.com") {
		override val isNsfwSource = true
	}

	@MangaSourceParser("MANGA_ISEKAI", "Manga Isekai", "en")
	class MangaIsekai(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_ISEKAI, "mangaisekai.com")

	@MangaSourceParser("MANGA_247", "247MANGA", "en")
	class Manga247(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_247, "247manga.com") {
		override val tagPrefix = "manhwa-genre/"
	}

	@MangaSourceParser("MANGA_365", "365Manga", "en")
	class Manga365(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_365, "365manga.com")

	@MangaSourceParser("MANGACLASH", "Mangaclash", "en")
	class Mangaclash(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGACLASH, "mangaclash.com")

	@MangaSourceParser("ZINMANGA", "ZINMANGA", "en")
	class Zinmanga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ZINMANGA, "zinmanga.com")
}
