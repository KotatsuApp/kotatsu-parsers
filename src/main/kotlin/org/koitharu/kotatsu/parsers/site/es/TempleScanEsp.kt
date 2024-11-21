package org.koitharu.kotatsu.parsers.site.es

import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TEMPLESCANESP", "TempleScanEsp", "es", ContentType.HENTAI)
internal class TempleScanEsp(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.TEMPLESCANESP) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST_ASC)

	override val configKeyDomain = ConfigKey.Domain("templescanesp.net")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val json = webClient.httpGet("https://apis.$domain/api/searchProject").parseJson().getJSONArray("response")
		return json.mapJSON {
			val href = "https://$domain/ver/${it.getString("slug")}"
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				coverUrl = it.getString("urlImg").orEmpty(),
				title = it.getString("name").orEmpty(),
				altTitle = it.getString("alternativeName").orEmpty(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val dateFormat = SimpleDateFormat("dd/mm/yyyy", sourceLocale)
		manga.copy(
			description = doc.selectFirst(".infoProject_projectInfo__786qu")?.text().orEmpty(),
			chapters = doc.body().select(".contenedor a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.selectFirst("span")?.text() ?: "Capítulo ${i + 1f}",
						number = i + 1f,
						volume = 0,
						url = href,
						uploadDate = parseChapterDate(
							dateFormat,
							a.selectFirst(".infoProject_dateChapter__BIuU7")?.text(),
						),
						source = source,
						scanlator = null,
						branch = null,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("main.contenedor img.readChapter_image__450v_").map { url ->
			val img = url.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet("há ", "hace ").startsWith(d) -> {
				parseRelativeDate(d)
			}

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("segundo", "second")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("minuto")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("hora", "horas")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("día", "días")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("meses", "mes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}
}
