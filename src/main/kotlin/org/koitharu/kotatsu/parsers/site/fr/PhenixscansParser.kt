package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getFloatOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("PHENIXSCANS", "PhenixScans", "fr")
internal class PhenixscansParser(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.PHENIXSCANS, 18) {

	override val configKeyDomain = ConfigKey.Domain("phenix-scans.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isMultipleTagsSupported = true,
		)

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.ALPHABETICAL,
			SortOrder.POPULARITY,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/api/front/manga")

			when {
				!filter.query.isNullOrEmpty() -> {
					if (page > 1) {
						return emptyList()
					}
					append("/search?query=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append("?page=")
					append(page.toString())
					append("&limit=18&sort=")
					when (order) {
						SortOrder.POPULARITY -> append("rating")
						SortOrder.UPDATED -> append("updatedAt")
						SortOrder.ALPHABETICAL -> append("title")
						else -> append("updatedAt")
					}

					if (filter.tags.isNotEmpty()) {
						append("&genre=")
						filter.tags.joinTo(this, separator = ",") { it.key }
					}

					filter.types.oneOrThrowIfMany()?.let {
						append("&type=")
						append(
							when (it) {
								ContentType.MANGA -> "Manga"
								ContentType.MANHWA -> "Manhwa"
								ContentType.MANHUA -> "Manhua"
								else -> ""
							},
						)
					}

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							when (it) {
								MangaState.ONGOING -> append("Ongoing")
								MangaState.FINISHED -> append("Completed")
								MangaState.PAUSED -> append("Hiatus")
								else -> append("")
							}
						}
					}

				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseJson().getJSONArray("mangas"))
	}

	private fun parseMangaList(json: JSONArray): List<Manga> {
		return json.mapJSON { j ->
			val slug = j.getString("slug")
			Manga(
				id = generateUid(j.getString("_id")),
				title = j.getString("title"),
				altTitles = emptySet(),
				url = slug,
				publicUrl = "https://$domain/manga/$slug",
				rating = j.getFloatOrDefault("averageRating", RATING_UNKNOWN * 10f) / 10f,
				contentRating = null,
				description = j.getStringOrNull("synopsis"),
				coverUrl = "https://cdn.phenix-scans.com/?url=https://api.phenix-scans.com/" + j.getString("coverImage") + "&output=webp&w=400&ll",
				tags = emptySet(),
				state = when (j.getStringOrNull("status")) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Hiatus" -> MangaState.FINISHED
					else -> null
				},
				authors = emptySet(),
				source = source,
			)
		}
	}

	private val dateFormat = SimpleDateFormat("d MMM yyyy", sourceLocale)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val mangaUrl = "https://$domain/manga/${manga.url}"
		val doc = webClient.httpGet(mangaUrl).parseHtml()

		manga.copy(
			tags = doc.select("div.project__content-tags a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast("tag="),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			chapters = doc.select(" div.project__chapters a.project__chapter")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					val name = a.selectFirst(".project__chapter-title")?.textOrNull()
					val dateText = a.selectFirst(".project__chapter-date")?.textOrNull()
					MangaChapter(
						id = generateUid(href),
						title = name,
						number = i.toFloat(),
						volume = 0,
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
		return doc.select("div.chapter-images img.chapter-image").map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val json = webClient.httpGet("https://api.$domain/front/manga?page=1&limit=18&sort=updatedAt").parseJson()
			.getJSONArray("genres")
		return json.mapJSONToSet {
			MangaTag(
				key = it.getString("_id"),
				title = it.getString("name").toTitleCase(sourceLocale),
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(
				" sec", " min", " h", " j", " sem", " mois", " année",
			).endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("il y a").startsWith(d) -> {
				parseRelativeDate(d)
			}

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> date.split(" ").map {
				if (it.contains(Regex("""\d\D\D"""))) {
					it.replace(Regex("""\D"""), "")
				} else {
					it
				}
			}.let { dateFormat.tryParse(it.joinToString(" ")) }

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second", "sec")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("min", "minute", "minutes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("heures", "heure", "h")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("jour", "jours", "j")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("sem", "semaine", "semaines").anyWordIn(date) -> cal.apply {
				add(
					Calendar.WEEK_OF_YEAR,
					-number,
				)
			}.timeInMillis

			WordSet("mois")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("année")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}
}
