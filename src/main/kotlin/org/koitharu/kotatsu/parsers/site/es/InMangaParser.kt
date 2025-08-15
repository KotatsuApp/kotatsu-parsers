package org.koitharu.kotatsu.parsers.site.es

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.toJSONObjectOrNull
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJson
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("INMANGA", "InManga", "es", ContentType.MANGA)
internal class InMangaParser(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaParserSource.INMANGA,
	pageSize = 10,
) {

	override val configKeyDomain = ConfigKey.Domain("inmanga.com")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.RELEVANCE,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = false,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = emptySet(),
			availableContentTypes = EnumSet.of(ContentType.MANGA),
		)
	}

	private val postHeaders = Headers.Builder()
		.add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		.add("X-Requested-With", "XMLHttpRequest")
		.build()

	private val imageCDN = "https://pack-yak.intomanga.com/"
	private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd", sourceLocale)

	private fun buildFormData(
		page: Int,
		order: SortOrder,
		query: String = "",
		genres: Set<String> = emptySet(),
	): Map<String, String> {
		val sortValue = when (order) {
			SortOrder.ALPHABETICAL -> "5"      // Nombre
			SortOrder.RELEVANCE -> "2"         // Relevancia
			SortOrder.POPULARITY -> "1"        // Vistos
			SortOrder.NEWEST -> "4"            // Recién agregado
			SortOrder.UPDATED -> "3"           // Recién actualizado
			else -> "3"                        // Default to Recién actualizado
		}

		val formData = mutableMapOf(
			"filter[queryString]" to query,
			"filter[skip]" to "${(page - 1) * 10}",
			"filter[take]" to "10",
			"filter[sortby]" to sortValue,
			"filter[broadcastStatus]" to "0",
			"filter[onlyFavorites]" to "false",
			"d" to "",
		)

		// Add genres
		if (genres.isEmpty()) {
			formData["filter[generes][]"] = "-1"
		} else {
			genres.forEachIndexed { index, genre ->
				formData["filter[generes][$index]"] = genre
			}
		}

		return formData
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val genres = filter.tags.map { it.key }.toSet()
		val formData = buildFormData(page, order, filter.query ?: "", genres)

		val response = webClient.httpPost(
			"https://$domain/manga/getMangasConsultResult".toHttpUrl(),
			formData,
			postHeaders,
		)

		val document = response.parseHtml()
		return document.select("body > a").map { element ->
			parseMangaFromElement(element)
		}
	}

	private fun parseMangaFromElement(element: Element): Manga {
		val url = element.attr("href")
		val title = element.select("h4.m0").text()
		val coverUrl = element.select("img").attr("abs:data-src")

		return Manga(
			id = url.hashCode().toLong(),
			title = title,
			altTitles = emptySet(),
			url = url,
			publicUrl = "https://$domain$url",
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = coverUrl,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			description = null,
			chapters = null,
			source = source,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val response = webClient.httpGet(manga.publicUrl.toHttpUrl())
		val document = response.parseHtml()

		val infoPanel = document.select("div.col-md-3 div.panel.widget")
		val contentPanel = document.select("div.col-md-9")

		val coverUrl = infoPanel.select("img").attr("abs:src")
		val statusText = infoPanel.select("a.list-group-item:contains(estado) span").text()
		val title = contentPanel.select("h1").text()
		val description = contentPanel.select("div.panel-body").text()

		val chapters = getChapters(manga)

		return manga.copy(
			coverUrl = coverUrl,
			title = title,
			description = description,
			state = parseStatus(statusText),
			chapters = chapters,
		)
	}

	private fun parseStatus(status: String?): MangaState? {
		return when {
			status == null -> null
			status.contains("En emisión") -> MangaState.ONGOING
			status.contains("Finalizado") -> MangaState.FINISHED
			else -> null
		}
	}

	private suspend fun getChapters(manga: Manga): List<MangaChapter> {
		val mangaId = manga.url.substringAfterLast("/")
		val response = webClient.httpGet(
			"https://$domain/chapter/getall?mangaIdentification=$mangaId".toHttpUrl(),
		)

		// The server returns a JSON with data property that contains a string with the JSON,
		// so is necessary to decode twice.
		val json = response.parseJson()
		val dataString = json.getStringOrNull("data") ?: return emptyList()

		val dataJson = dataString.toJSONObjectOrNull() ?: return emptyList()
		if (!dataJson.getBooleanOrDefault("success", false)) {
			return emptyList()
		}

		val chaptersArray = dataJson.optJSONArray("result") ?: return emptyList()

		return chaptersArray.mapJSON { chapterJson ->
			parseChapterFromJson(chapterJson)
		}.sortedBy { it.number }
	}

	private fun parseChapterFromJson(chapterJson: JSONObject): MangaChapter {
		val identification = chapterJson.getStringOrNull("Identification") ?: ""
		val friendlyChapterNumber = chapterJson.getStringOrNull("FriendlyChapterNumber") ?: ""
		val number = chapterJson.optDouble("Number").toFloat()
		val registrationDate = chapterJson.getStringOrNull("RegistrationDate") ?: ""

		return MangaChapter(
			id = identification.hashCode().toLong(),
			title = "Chapter $friendlyChapterNumber",
			number = number,
			volume = 0,
			url = "/chapter/chapterIndexControls?identification=$identification",
			scanlator = null,
			uploadDate = parseChapterDate(registrationDate),
			branch = null,
			source = source,
		)
	}

	private fun parseChapterDate(dateString: String): Long {
		return try {
			chapterDateFormat.parse(dateString)?.time ?: 0L
		} catch (_: Exception) {
			0L
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val response = webClient.httpGet(
			"https://$domain${chapter.url}".toHttpUrl(),
		)
		val document = response.parseHtml()

		val ch = document.select("[id=\"FriendlyChapterNumberUrl\"]").attr("value")
		val title = document.select("[id=\"FriendlyMangaName\"]").attr("value")

		return document.select("img.ImageContainer").mapIndexed { index, img ->
			val imageId = img.attr("id")
			val imageUrl = "$imageCDN/images/manga/$title/chapter/$ch/page/${index + 1}/$imageId"

			MangaPage(
				id = index.toLong(),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}
}
