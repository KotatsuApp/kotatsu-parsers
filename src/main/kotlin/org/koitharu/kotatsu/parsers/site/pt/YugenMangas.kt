package org.koitharu.kotatsu.parsers.site.pt

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("YUGENMANGAS", "YugenApp", "pt")
internal class YugenMangas(context: MangaLoaderContext) :
	SinglePageMangaParser(context, MangaParserSource.YUGENMANGAS) {

	override val configKeyDomain = ConfigKey.Domain("yugenmangasbr.voblog.xyz")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		val json = when {

			!filter.query.isNullOrEmpty() -> {

				val url = buildString {
					append("https://api.")
					append(domain)
					append("/api/series/?search=")
					append(filter.query.urlEncoded())
				}
				webClient.httpGet(url).parseJsonArray()
			}

			else -> {

				if (order == SortOrder.UPDATED) {
					val url = buildString {
						append("https://api.")
						append(domain)
						append("/api/latest_updates/")
					}
					webClient.httpGet(url).parseJsonArray()
				} else {
					val url = buildString {
						append("https://api.")
						append(domain)
						append("/api/series_novels/all_series/")
					}
					webClient.httpGet(url).parseJson().getJSONArray("series")
				}

			}
		}

		return json.mapJSON { j ->
			val slug = j.getString("slug")
			val cover = if (!j.getString("cover").startsWith("https://")) {
				// Some covers don't have the "/" so we ensure that the URL will be spelled correctly.
				"https://api.$domain/media/" + j.getString("cover").removePrefix("/")
			} else {
				j.getString("cover")
			}
			Manga(
				id = generateUid(slug),
				url = slug,
				publicUrl = slug,
				title = j.getString("name"),
				coverUrl = cover,
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val detailManga =
			webClient.httpPost("https://api.$domain/api/serie/serie_details/${manga.url}", emptyMap()).parseJson()
		val body = JSONObject()
		body.put("serie_slug", manga.url)
		val chapterManga =
			webClient.httpPost("https://api.$domain/api/chapters/get_chapters_by_serie/", body).parseJson()
				.getJSONArray("chapters")
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		return manga.copy(
			description = detailManga.getString("synopsis"),
			coverUrl = detailManga.getString("cover"),
			altTitle = detailManga.getString("alternative_names"),
			author = detailManga.getString("author"),
			state = detailManga.getString("status")?.let {
				when (it) {
					"ongoing" -> MangaState.ONGOING
					"completed" -> MangaState.FINISHED
					"hiatus" -> MangaState.PAUSED
					else -> null
				}
			},
			chapters = chapterManga.mapJSON { j ->
				val url = "https://api.$domain/api/serie/${manga.url}/chapter/${j.getString("slug")}/images/imgs/get/"
				MangaChapter(
					id = generateUid(url),
					name = j.getString("name"),
					number = j.getString("name").removePrefix("Capítulo ").toFloat(),
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = parseChapterDate(
						dateFormat,
						j.getString("upload_date"),
					),
					branch = null,
					source = source,
				)
			}.sortedBy { it.name },
		)
	}

	private fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" atrás") -> parseRelativeDate(date)
			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet("dia", "dias").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
			WordSet("hora", "horas").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
			else -> 0
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val jsonPages = webClient.httpPost(chapter.url, emptyMap()).parseJson().getJSONArray("chapter_images")
		val pages = ArrayList<MangaPage>(jsonPages.length())
		for (i in 0 until jsonPages.length()) {
			val img = "https://api.$domain/${jsonPages.getString(i)}"
			pages.add(
				MangaPage(
					id = generateUid(img),
					url = img,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
