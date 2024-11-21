package org.koitharu.kotatsu.parsers.site.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANHWASMEN", "ManhwasMen", "en", type = ContentType.HENTAI)
internal class ManhwasMen(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANHWASMEN, pageSize = 30, searchPageSize = 30) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manhwas.men")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.POPULARITY)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/manga-list")
			append("?page=")
			append(page.toString())
			when {
				!filter.query.isNullOrEmpty() -> {
					append("&search=")
					append(filter.query.urlEncoded())
				}

				else -> {


					if (filter.tags.isNotEmpty() && filter.states.isNotEmpty()) {
						throw IllegalArgumentException("The source supports one filter at a time")
					}

					filter.tags.oneOrThrowIfMany()?.let {
						append("&genero=")
						append(it.key)
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&estado=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "complete"
								else -> ""
							},
						)
					}
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("ul.animes li").map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = li.selectFirst("img")?.src().orEmpty(),
				title = li.selectFirst(".title")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val tags = webClient.httpGet("https://$domain/manga-list").parseHtml()
			.selectLastOrThrow(".filter-bx .form-group select.custom-select").select("option").drop(1)
		return tags.mapToSet { option ->
			MangaTag(
				key = option.attr("value").substringAfterLast('='),
				title = option.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		return manga.copy(
			tags = doc.body().select(".genres a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast('='),
					title = a.text(),
					source = source,
				)
			},
			description = doc.select(".sinopsis").html(),
			state = when (doc.selectLast("span.anime-type-peli")?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"complete" -> MangaState.FINISHED
				else -> null
			},
			chapters = doc.select(".episodes-list li").mapChapters(reversed = true) { i, li ->
				val url = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				MangaChapter(
					id = generateUid(url),
					name = li.selectFirstOrThrow(".flex-grow-1 span").text(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = parseChapterDate(
						simpleDateFormat,
						li.selectLastOrThrow(".flex-grow-1 span").text(),
					),
					branch = null,
					source = source,
				)
			},
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") -> parseRelativeDate(date)
			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minute", "minutes").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("week", "weeks").anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("chapter_imgs")
		return doc.select("img").map { img ->
			val url = img.requireSrc().toRelativeUrl(domain)
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
