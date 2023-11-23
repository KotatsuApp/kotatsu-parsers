package org.koitharu.kotatsu.parsers.site.pt

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("YUGENMANGAS", "YugenMangas.net.br", "pt")
class YugenMangas(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.YUGENMANGAS, 28) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.UPDATED)
	override val configKeyDomain = ConfigKey.Domain("yugenmangas.net.br")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val json =
			if (!query.isNullOrEmpty()) {
				if (page > 1) {
					return emptyList()
				}
				val url = buildString {
					append("https://api.")
					append(domain)
					append("/api/series/list/?query=")
					append(query.urlEncoded())
				}
				webClient.httpGet(url).parseJsonArray()
			} else {
				if (page > 1) {
					return emptyList()
				}
				val url = buildString {
					append("https://api.")
					append(domain)
					append("/api/all_series/?page=1")
				}
				webClient.httpGet(url).parseJson().getJSONArray("series")
			}

		return json.mapJSON { j ->
			val slug = j.getString("slug")
			Manga(
				id = generateUid(slug),
				url = slug,
				publicUrl = slug,
				title = j.getString("name"),
				coverUrl = j.getString("cover"),
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
			webClient.httpPost("https://api.$domain/api/serie_details/${manga.url}", emptyMap()).parseJson()
		val body = JSONObject()
		body.put("serie_slug", manga.url)
		val chapterManga = webClient.httpPost("https://api.$domain/api/get_chapters_by_serie/", body).parseJson()
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
			chapters = chapterManga.mapJSONIndexed { i, j ->
				val url = "https://api.$domain/api/serie/${manga.url}/chapter/${j.getString("slug")}/images/imgs/"
				MangaChapter(
					id = generateUid(url),
					name = j.getString("name"),
					number = j.getString("name").removePrefix("Capítulo ").toInt(),
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
			val img = "https://$domain/${jsonPages.getString(i)}"
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

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()
}
