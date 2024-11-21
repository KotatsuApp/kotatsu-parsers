package org.koitharu.kotatsu.parsers.site.fuzzydoodle

import androidx.collection.scatterSetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class FuzzyDoodleParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.NEWEST)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
		),
	)

	@JvmField
	protected val ongoing = scatterSetOf(
		"en cours",
		"ongoing",
		"مستمر",
	)

	@JvmField
	protected val finished = scatterSetOf(
		"terminé",
		"dropped",
		"cancelled",
		"متوقف",
	)

	@JvmField
	protected val abandoned = scatterSetOf(
		"canceled",
		"cancelled",
		"dropped",
		"abandonné",
	)

	@JvmField
	protected val paused = scatterSetOf(
		"hiatus",
		"on Hold",
		"en pause",
		"en attente",
	)

	protected open val ongoingValue = "ongoing"
	protected open val finishedValue = "completed"
	protected open val pausedValue = "haitus"
	protected open val abandonedValue = "dropped"

	protected open val mangaValue = "manga"
	protected open val manhwaValue = "manhwa"
	protected open val manhuaValue = "manhua"
	protected open val comicsValue = "bande-dessinee"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/manga?page=")
			append(page.toString())

			append("&type=")
			filter.types.oneOrThrowIfMany().let {
				append(
					when (it) {
						ContentType.MANGA -> mangaValue
						ContentType.MANHWA -> manhwaValue
						ContentType.MANHUA -> manhuaValue
						ContentType.COMICS -> comicsValue
						else -> ""
					},
				)
			}

			filter.query?.let {
				append("&title=")
				append(filter.query.urlEncoded())
			}
			append("&status=")
			filter.states.oneOrThrowIfMany()?.let {
				append(
					when (it) {
						MangaState.ONGOING -> ongoingValue
						MangaState.FINISHED -> finishedValue
						MangaState.PAUSED -> pausedValue
						MangaState.ABANDONED -> abandonedValue
						else -> ""
					},
				)
			}

			filter.tags.forEach {
				append("&")
				append("genre[]".urlEncoded())
				append("=")
				append(it.key)
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open val selectMangas = "div#card-real"

	protected open fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectMangas).mapNotNull { div ->
			val href = div.selectFirst("a")?.attr("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("h2")?.text().orEmpty(),
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

	protected open val selectAltTitle = "div.flex gap-1:contains(Alternative Titles:) span"
	protected open val selectState = "a[href*=status] span"
	protected open val selectAuthor =
		"div#buttons + div.hidden p:contains(Auteur) span, div#buttons + div.hidden p:contains(Author) span, div#buttons + div.hidden p:contains(المؤلف) span"
	protected open val selectDescription = "div:has(> p#description) p"
	protected open val selectTagManga = "div.flex > a.inline-block"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(mangaUrl).parseHtml()

		val maxPageChapterSelect = doc.select("ul.pagination li[onclick]")
		var maxPageChapter = 1
		if (!maxPageChapterSelect.isNullOrEmpty()) {
			maxPageChapterSelect.map {
				val i = it.attr("onclick").substringAfterLast("=").substringBefore("'").toInt()
				if (i > maxPageChapter) {
					maxPageChapter = i
				}
			}
		}

		manga.copy(
			altTitle = doc.selectLast(selectAltTitle)?.text(),
			state = when (doc.selectFirst(selectState)?.text()?.lowercase().orEmpty()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			},
			author = doc.selectFirst(selectAuthor)?.text().orEmpty(),
			description = doc.select(selectDescription).text(),
			tags = doc.select(selectTagManga).mapToSet {
				val key = it.attr("href").substringAfterLast('=')
				MangaTag(
					key = key,
					title = it.text(),
					source = source,
				)
			},
			chapters = run {
				if (maxPageChapter == 1) {
					parseChapters(doc)
				} else {
					coroutineScope {
						val result = ArrayList(parseChapters(doc))
						result.ensureCapacity(result.size * maxPageChapter)
						(2..maxPageChapter).map { i ->
							async {
								loadChapters(mangaUrl, i)
							}
						}.awaitAll()
							.flattenTo(result)
						result
					}
				}
			}.reversed(),
		)
	}


	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?page=$page").parseHtml().body())
	}

	protected open val datePattern = "MMMM d, yyyy"
	protected open val selectChapters = "div#chapters-list > a[href]"

	private fun parseChapters(doc: Element): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select(selectChapters)
			.mapChapters { _, a ->
				val href = a.attrAsRelativeUrl("href")
				val name = a.selectFirst("div.gap-2, #item-title")?.text().orEmpty()
				val dateText = a.selectFirst("div.gap-3 span, div:has( #item-title) span.mt-1")?.text()
				val chapterN = href.substringAfterLast('/').replace("-", ".").replace("[^0-9.]".toRegex(), "").toFloat()
				MangaChapter(
					id = generateUid(href),
					name = name,
					number = chapterN,
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
			}
	}

	protected open val selectPages = "div#chapter-container > img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPages).map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open val selectTagsList = "div.mt-1 div.items-center:has(label)"

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/manga").parseHtml()
		return doc.select(selectTagsList).mapNotNullToSet {
			val key = it.selectFirst("input")?.attr("value") ?: return@mapNotNullToSet null
			MangaTag(
				key = key,
				title = it.selectFirst("label")?.text() ?: key,
				source = source,
			)
		}
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(" ago", "مضت").endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("il y a", "منذ").startsWith(d) -> {
				parseRelativeDate(d)
			}

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("detik", "segundo", "second", "ثوان")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("menit", "dakika", "min", "minute", "minutes", "minuto", "mins", "phút", "минут", "دقيقة")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h", "ساعات", "ساعة")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("hari", "gün", "jour", "día", "dia", "day", "days", "d", "день")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months", "أشهر", "mois")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("week", "weeks", "semana", "semanas")
				.anyWordIn(date) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis

			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}
}
