package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArrayMap
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAPARK", "MangaPark")
internal class MangaPark(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.MANGAPARK, pageSize = 36) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.allOf(SortOrder::class.java)

	override val availableStates: Set<MangaState> = EnumSet.allOf(MangaState::class.java)

	override val availableContentRating: Set<ContentRating> = EnumSet.of(ContentRating.SAFE)

	override val isTagsExclusionSupported: Boolean = true

	override val configKeyDomain = ConfigKey.Domain("mangapark.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private val tagsMap = SuspendLazy(::parseTags)

	init {
		context.cookieJar.insertCookies(domain, "nsfw", "2")
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search?page=")
			append(page.toString())
			when (filter) {
				is MangaListFilter.Search -> {
					append("&word=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					append("&genres=")
					if (filter.tags.isNotEmpty()) {
						appendAll(filter.tags, ",") { it.key }
					}

					append("|")
					if (filter.tagsExclude.isNotEmpty()) {
						appendAll(filter.tagsExclude, ",") { it.key }
					}

					if (filter.contentRating.isNotEmpty()) {
						filter.contentRating.oneOrThrowIfMany()?.let {
							append(
								when (it) {
									ContentRating.SAFE -> append(",gore,bloody,violence,ecchi,adult,mature,smut,hentai")
									else -> append("")
								},
							)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								MangaState.PAUSED -> "hiatus"
								MangaState.ABANDONED -> "cancelled"
								MangaState.UPCOMING -> "pending"
							},
						)
					}

					append("&sortby=")
					append(
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> "views_d000"
							SortOrder.UPDATED -> "field_update"
							SortOrder.NEWEST -> "field_create"
							SortOrder.ALPHABETICAL -> "field_name"
							SortOrder.RATING -> "field_score"
							else -> ""

						},
					)

					filter.locale?.let {
						append("&lang=")
						append(it.language)
					}
				}

				null -> append("&sortby=field_update")
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.grid.gap-5 div.flex.border-b").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("h3")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.text-yellow-500")?.text()?.toFloatOrNull()?.div(10F) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return tagsMap.get().values.toSet()
	}

	private suspend fun parseTags(): Map<String, MangaTag> {
		val tagElements = webClient.httpGet("https://$domain/search").parseHtml()
			.select("div.flex-col:contains(Genres) div.whitespace-nowrap")
		val tagMap = ArrayMap<String, MangaTag>(tagElements.size)
		for (el in tagElements) {
			val name = el.selectFirstOrThrow("span.whitespace-nowrap").text().toTitleCase(sourceLocale)
			if (name.isEmpty()) continue
			tagMap[name] = MangaTag(
				title = name,
				key = el.attr("q:key") ?: continue,
				source = source,
			)
		}
		return tagMap
	}

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
		Locale("af"), Locale("sq"), Locale("am"), Locale("ar"), Locale("hy"),
		Locale("az"), Locale("be"), Locale("bn"), Locale("zh_hk"), Locale("zh_tw"),
		Locale.CHINESE, Locale("ceb"), Locale("ca"), Locale("km"), Locale("my"),
		Locale("bg"), Locale("bs"), Locale("hr"), Locale("cs"), Locale("da"),
		Locale("nl"), Locale.ENGLISH, Locale("et"), Locale("fo"), Locale("fil"),
		Locale("fi"), Locale("he"), Locale("ha"), Locale("jv"), Locale("lb"),
		Locale("mn"), Locale("ro"), Locale("si"), Locale("ta"), Locale("uz"),
		Locale("ur"), Locale("tg"), Locale("sd"), Locale("pt_br"), Locale("mo"),
		Locale("lt"), Locale.JAPANESE, Locale.ITALIAN, Locale("ht"), Locale("lv"),
		Locale("mr"), Locale("pt"), Locale("sn"), Locale("sv"), Locale("uk"),
		Locale("tk"), Locale("sw"), Locale("st"), Locale("pl"), Locale("mi"),
		Locale("lo"), Locale("ga"), Locale("gu"), Locale("gn"), Locale("id"),
		Locale("ky"), Locale("mt"), Locale("fa"), Locale("sh"), Locale("es_419"),
		Locale("tr"), Locale("to"), Locale("vi"), Locale("es"), Locale("sr"),
		Locale("ps"), Locale("ml"), Locale("ku"), Locale("ig"), Locale("el"),
		Locale.GERMAN, Locale("is"), Locale.KOREAN, Locale("ms"), Locale("ny"), Locale("sm"),
		Locale("so"), Locale("ti"), Locale("zu"), Locale("yo"), Locale("th"),
		Locale("sl"), Locale("ru"), Locale("no"), Locale("mg"), Locale("kk"),
		Locale("hu"), Locale("ka"), Locale.FRENCH, Locale("hi"), Locale("kn"),
		Locale("mk"), Locale("ne"), Locale("rm"), Locale("sk"), Locale("te"),
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tagMap = tagsMap.get()
		val selectTag = doc.select("div[q:key=30_2] span.whitespace-nowrap")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		val nsfw = tags.any { t -> t.key == "hentai" || t.key == "adult" }
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		manga.copy(
			altTitle = doc.selectFirst("div[q:key=tz_2]")?.text().orEmpty(),
			author = doc.selectFirst("div[q:key=tz_4]")?.text().orEmpty(),
			description = doc.selectFirst("react-island[q:key=0a_9]")?.html().orEmpty(),
			state = when (doc.selectFirst("span[q:key=Yn_5]")?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				"hiatus" -> MangaState.PAUSED
				"cancelled" -> MangaState.ABANDONED
				else -> null
			},
			tags = tags,
			isNsfw = nsfw,
			chapters = doc.body().select("div.group.flex div.px-2").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val dateText = div.selectFirst("span[q:key=Ee_0]")?.text()
				MangaChapter(
					id = generateUid(href),
					name = a.text(),
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
			},
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") -> parseRelativeDate(date)
			d.startsWith("just now") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
			WordSet("minute", "minutes", "mins", "min").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("hour", "hours").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			WordSet("day", "days").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val script = if (doc.selectFirst("script:containsData(comic-)") != null) {
			doc.selectFirstOrThrow("script:containsData(comic-)").data()
				.substringAfterLast("\"comic-")
		} else {
			doc.selectFirstOrThrow("script:containsData(manga-)").data()
				.substringAfterLast("\"manga-")
		}
		return Regex("\"(https?:.+?)\"")
			.findAll(script)
			.mapNotNullTo(ArrayList()) {
				val url = it.groupValues.getOrNull(1) ?: return@mapNotNullTo null
				if (url.contains("/comic/") || url.contains("/manga/") || url.contains("/image/mpup/")) {
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				} else {
					return@mapNotNullTo null
				}
			}
	}
}
