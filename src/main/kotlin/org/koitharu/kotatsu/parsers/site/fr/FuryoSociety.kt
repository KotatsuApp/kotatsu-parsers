package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("FURYOSOCIETY", "FuryoSociety", "fr")
internal class FuryoSociety(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.FURYOSOCIETY, 0) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("furyosociety.com")

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isSearchSupported = false

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/fs_favicon/favicon-32x32.png", 32, null),
			),
			domain,
		)
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if (page > 1) {
			return emptyList()
		}

		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					throw IllegalArgumentException(ErrorMessages.SEARCH_NOT_SUPPORTED)
				}

				is MangaListFilter.Advanced -> {

					if (filter.sortOrder == SortOrder.ALPHABETICAL) {
						append("/mangas")
					}
				}

				null -> {}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.fs-card-container div.grid-item-container").ifEmpty {
			doc.select("div.container-tight.latest table tr")
		}.map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (div.selectFirst("div.media-body") ?: div.selectFirst("a"))?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = false,
			)
		}
	}


	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()


	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = getChapters(doc)
		manga.copy(
			description = doc.selectFirstOrThrow("div.fs-comic-description").html(),
			chapters = chaptersDeferred,
			isNsfw = doc.selectFirst(".adult-text") != null,
		)
	}


	private fun getChapters(doc: Document): List<MangaChapter> {
		return doc.body().select("div.list.fs-chapter-list div.element").mapChapters(reversed = true) { i, div ->
			val a = div.selectFirstOrThrow("div.title a")
			val href = a.attrAsRelativeUrl("href")
			val dateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
			val dateText = div.selectFirstOrThrow("div.meta_r").text().replace("Hier", "1 jour")
			MangaChapter(
				id = generateUid(href),
				name = div.selectFirstOrThrow("div.title").text() + " : " + div.selectFirstOrThrow("div.name").text(),
				number = i + 1f,
				volume = 0,
				url = href,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.main-img img").map { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
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
			d.startsWith("il y a") || // Handle translated 'ago' in French.
				d.endsWith(" an") || d.endsWith(" ans") ||
				d.endsWith(" mois") ||
				d.endsWith(" jour") || d.endsWith(" jours") ||
				d.endsWith(" heure") || d.endsWith(" heures") ||
				d.endsWith(" seconde") || d.endsWith(" secondes") ||
				d.endsWith(" minute") || d.endsWith(" minutes") -> parseRelativeDate(date)

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("seconde", "secondes").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("heure", "heures").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("jour", "jours").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("mois").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("an", "ans").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
