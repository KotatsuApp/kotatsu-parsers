package org.koitharu.kotatsu.parsers.site.fr

import okhttp3.Headers
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

@MangaSourceParser("LUGNICASCANS", "Lugnica Scans", "fr")
internal class LugnicaScans(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.LUGNICASCANS, 10) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.UPDATED,
	)

	override val configKeyDomain = ConfigKey.Domain("lugnica-scans.com")

	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	init {
		context.cookieJar.insertCookies(
			domain,
			"reader_render=continue;",
		)
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/favicon/favicon-32x32.png", 32, null),
			),
			domain,
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val url = buildString {
			append("https://")
			append(domain)
			if (sortOrder == SortOrder.ALPHABETICAL) {
				append("/mangas/")
				// just to stop the search of the ALPHABETICAL page because it contains all the manga and has no page function ( to change if there is a better method to stop the search )
				if (page == 2) {
					append(page.toString()) // juste for break
				}
			}

			if (sortOrder == SortOrder.UPDATED) {
				append("/api/manga/home/getlast/")
				append(page.toString())
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		if (sortOrder == SortOrder.UPDATED) {
			return doc.select(".last_chapters-element")
				.map { div ->
					val a = div.selectFirstOrThrow("a.last_chapters-title")
					val href = a.attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = a.text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = div.selectFirstOrThrow(".last_chapters-rate").ownText().toFloatOrNull()?.div(5f)
							?: -1f,
						isNsfw = false,
						coverUrl = div.selectFirstOrThrow(".last_chapters-image img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		} else {
			val root = doc.selectFirstOrThrow(".catalog")
			return root.select("div.element")
				.map { div ->
					val href = div.selectFirstOrThrow("a").attrAsAbsoluteUrl("href")
					Manga(
						id = generateUid(href),
						title = div.select("a.title").text(),
						altTitle = null,
						url = href,
						publicUrl = href.toAbsoluteUrl(domain),
						rating = div.selectFirstOrThrow("div.stats").lastElementChild()?.ownText()?.toFloatOrNull()
							?.div(5f) ?: -1f,
						isNsfw = false,
						coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
						tags = setOf(),
						state = null,
						author = null,
						source = source,
					)
				}
		}


	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)

		return manga.copy(
			altTitle = null,
			state = when (root.select("div.manga-tags")[3].select("a").text()) {
				"En Cours" -> MangaState.ONGOING
				"Fini", "Abandonné", "Licencier" -> MangaState.FINISHED
				else -> null
			},

			// Lists the tags but there is no search on the site so it will just come back to the a-z or last list.
			tags = root.select("div.manga-tags")[1].select("a").mapNotNullToSet { a ->
				MangaTag(
					key = a.text(),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = root.select("div.manga-staff").text(),
			description = root.selectFirst("div.manga-description div")?.text(),
			chapters = root.select("div.manga-chapters_wrapper div.manga-chapter")
				.mapChapters(reversed = true) { i, div ->

					val a = div.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					val name = a.text()

					val dateText = div.select("span").last()?.text()
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i,
						url = href,
						scanlator = null,
						uploadDate = parseChapterDate(
							dateFormat,
							dateText,
						),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().requireElementById("forgen_reader")
		return root.select("img").map { img ->
			val url = img.attrAsRelativeUrlOrNull("data-src") ?: img.attrAsRelativeUrlOrNull("src")
			?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> = emptySet()

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.startsWith("il y a") -> parseRelativeDate(date)

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet("jour", "jours").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("heure", "heures").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("seconde", "secondes").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("mois").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("année", "années").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			WordSet("semaine", "semaines").anyWordIn(date) -> cal.apply {
				add(
					Calendar.WEEK_OF_MONTH,
					-number,
				)
			}.timeInMillis

			else -> 0
		}
	}
}
