package org.koitharu.kotatsu.parsers.site.pt

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.parseSafe
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.text.SimpleDateFormat
import java.util.EnumSet

@MangaSourceParser("SUSSYSCAN", "SussyScan", "pt")
internal class SussyScan(context: MangaLoaderContext) : PagedMangaParser(
	context,
	source = MangaParserSource.SUSSYSCAN,
	pageSize = 24,
	searchPageSize = 15,
) {
	override val configKeyDomain = ConfigKey.Domain("sussytoons.wtf")
	private val apiUrl = "https://api.sussytoons.wtf"
	private val cdnUrl = "https://cdn.sussytoons.site"
	private val scanId = 1

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_TODAY,
		SortOrder.POPULARITY_WEEK,
		SortOrder.POPULARITY_MONTH,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchAvailableTags(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.PAUSED,
				MangaState.ABANDONED,
			),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHUA,
				ContentType.MANHWA,
				ContentType.HENTAI,
			),
		)
	}

	private val apiHeaders: Headers
		get() = Headers.Builder()
			.add("Referer", "https://$domain/")
			.add("scan-id", scanId.toString())
			.build()

	private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", sourceLocale)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val genId = when {
			filter.types.oneOrThrowIfMany() == ContentType.HENTAI -> "5"
			else -> "1"
		}

		val url = when {
			!filter.query.isNullOrEmpty() || filter.tags.isNotEmpty() ||
				filter.states.isNotEmpty() -> buildSearchUrl(page, filter)

			// Popularity rankings
			order in setOf(
				SortOrder.POPULARITY,
				SortOrder.POPULARITY_TODAY,
				SortOrder.POPULARITY_WEEK,
				SortOrder.POPULARITY_MONTH,
			) -> {
				val period = when (order) {
					SortOrder.POPULARITY_TODAY -> "dia"
					SortOrder.POPULARITY_WEEK -> "semana"
					SortOrder.POPULARITY_MONTH -> "mes"
					else -> "geral" // all time
				}
				"$apiUrl/obras/ranking".toHttpUrl().newBuilder()
					.addQueryParameter("periodo", period)
					.addQueryParameter("limite", pageSize.toString())
					.addQueryParameter("pagina", page.toString())
					.addQueryParameter("gen_id", genId)
					.build()
			}
			// Default to updated
			else -> {
				"$apiUrl/obras/novos-capitulos".toHttpUrl().newBuilder()
					.addQueryParameter("limite", pageSize.toString())
					.addQueryParameter("pagina", page.toString())
					.addQueryParameter("gen_id", genId)
					.build()
			}
		}

		val response = webClient.httpGet(url, apiHeaders).parseJson()
		val results = response.optJSONArray("resultados") ?: return emptyList()
		return results.mapJSON { parseMangaFromJson(it) }
	}

	private fun buildSearchUrl(page: Int, filter: MangaListFilter): HttpUrl {
		val builder = "$apiUrl/obras".toHttpUrl().newBuilder()
			.addQueryParameter("obr_nome", filter.query ?: "")
			.addQueryParameter("limite", "15")
			.addQueryParameter("pagina", page.toString())

		val isHentai = filter.types.firstOrNull() == ContentType.HENTAI

		if (isHentai) builder.addQueryParameter("gen_id", "5") else builder.addQueryParameter("todos_generos", "true")

		// Add tags
		filter.tags.forEach { tag ->
			builder.addQueryParameter("tags[]", tag.key)
		}

		// Add format (content type)
		filter.types.oneOrThrowIfMany().let { contentType ->
			val type = when (contentType) {
				ContentType.MANHWA -> "1"
				ContentType.MANHUA -> "2"
				ContentType.MANGA -> "3"
				else -> null
			}
			type?.let { builder.addQueryParameter("formt_id", it) }
		}

		// Add status
		filter.states.firstOrNull()?.let { state ->
			val statusId = when (state) {
				MangaState.ONGOING -> "1"
				MangaState.FINISHED -> "2"
				MangaState.PAUSED -> "3"
				MangaState.ABANDONED -> "4"
				else -> null
			}
			statusId?.let { builder.addQueryParameter("stt_id", it) }
		}

		return builder.build()
	}

	private fun parseMangaFromJson(json: JSONObject): Manga {
		val id = json.getInt("obr_id")
		val name = json.getString("obr_nome")
		val slug = json.optString("obr_slug", "").ifEmpty {
			name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
		}
		val coverPath = json.optString("obr_imagem", "")

		val coverUrl = when {
			coverPath.startsWith("http") -> coverPath
			coverPath.startsWith("wp-content") -> "$cdnUrl/$coverPath"
			coverPath.isNotEmpty() -> "$cdnUrl/scans/$scanId/obras/$id/$coverPath"
			else -> ""
		}

		val isNsfw = json.optBoolean("obr_mais_18", false)
		val rating = json.optString("rating").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN

		return Manga(
			id = generateUid(id.toLong()),
			title = name,
			url = "/obra/$id/$slug",
			publicUrl = "https://$domain/obra/$id/$slug",
			coverUrl = coverUrl,
			source = source,
			rating = rating,
			altTitles = emptySet(),
			contentRating = if (isNsfw) ContentRating.ADULT else ContentRating.SAFE,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaId = manga.url.substringAfter("/obra/").substringBefore("/")
		val response = webClient.httpGet("$apiUrl/obras/$mangaId", apiHeaders).parseJson()
		val mangaJson = response.optJSONObject("resultado") ?: throw Exception("Manga not found")

		val description = mangaJson.optString("obr_descricao")
			.replace(Regex("</?strong>"), "")
			.replace("\\/", "/")
			.replace(Regex("\\s+"), " ")
			.trim()

		val status = mangaJson.optJSONObject("status")
			?.optString("stt_nome")
			?.let { parseStatus(it) }

		val tags = mangaJson.optJSONArray("tags")?.mapJSON { tagJson ->
			val tagName = tagJson.getString("tag_nome")
			MangaTag(
				key = tagJson.optInt("tag_id").toString(),
				title = tagName.toTitleCase(),
				source = source,
			)
		}?.toSet() ?: emptySet()

		val chapters = mangaJson.optJSONArray("capitulos")?.mapJSON { chapterJson ->
			parseChapter(chapterJson)
		}?.asReversed() ?: emptyList()

		return manga.copy(
			title = mangaJson.optString("obr_nome", manga.title),
			description = description,
			state = status,
			tags = tags,
			chapters = chapters,
		)
	}

	private fun parseChapter(json: JSONObject): MangaChapter {
		val chapterId = json.getInt("cap_id")
		val chapterName = json.getString("cap_nome")
		val chapterDate = json.optString("cap_lancado_em")

		val chapterNumber = json.optDouble("cap_numero").let {
			if (it > 0) it.toFloat() else {
				chapterName
					.substringAfter("Capítulo ", "")
					.substringBefore(" ")
					.replace(",", ".")
					.toFloat()
			}
		}

		return MangaChapter(
			id = generateUid(chapterId.toLong()),
			title = chapterName,
			number = chapterNumber,
			url = "/capitulo/$chapterId",
			uploadDate = chapterDateFormat.parseSafe(chapterDate),
			source = source,
			volume = 0,
			scanlator = null,
			branch = null,
		)
	}

	private fun parseStatus(status: String): MangaState? = when (status.lowercase()) {
		"em andamento" -> MangaState.ONGOING
		"completo" -> MangaState.FINISHED
		"hiato" -> MangaState.PAUSED
		"cancelado" -> MangaState.ABANDONED
		else -> null
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterId = chapter.url.substringAfter("/capitulo/")

		val pageHeaders = apiHeaders.newBuilder()
			.build()

		// Fetch chapter data from API
		val apiPath = "c9812736812/$chapterId"
		val response = webClient.httpGet("$apiUrl/$apiPath", pageHeaders).parseJson()
		val chapterData = response.optJSONObject("resultado") ?: throw Exception("Chapter data not found")

		// Parse pages from the response
		val pagesArray = chapterData.optJSONArray("cap_paginas")
			?: chapterData.optJSONArray("paginas")
			?: throw Exception("No pages found in chapter")

		val mangaId = chapterData.optJSONObject("obra")?.optInt("obr_id")
			?: throw Exception("Manga ID not found")

		val chapterNumber = chapterData.optDouble("cap_numero").let { num ->
			when {
				num > 0 -> {
					if (num % 1 == 0.0) num.toInt().toString() else num.toString().replace(".", "_")
				}

				else -> {
					chapterData.optString("cap_nome", "")
						.substringAfter("Capítulo ", "")
						.substringBefore(" ")
						.replace(",", ".")
						.replace(".", "_")
						.ifEmpty { "0" }
				}
			}
		}

		return pagesArray.mapJSONNotNull { pageJson ->
			val pageSrc = pageJson.optString("src")

			if (pageSrc.isEmpty()) return@mapJSONNotNull null

			val imageUrl = when {
				// Already a full URL
				pageSrc.startsWith("http") -> pageSrc
				// WordPress manga path, looks like: "manga_.../hash/001.webp"
				pageSrc.startsWith("manga_") -> "$cdnUrl/wp-content/uploads/WP-manga/data/$pageSrc"
				// WordPress legacy path: "wp-content/uploads/..."
				pageSrc.startsWith("wp-content") -> "$cdnUrl/$pageSrc"
				// Simple filename (like "001.webp")
				else -> {
					val safeChapterNumber = chapterNumber.replace(".", "_")
					"$cdnUrl/scans/$scanId/obras/$mangaId/capitulos/$safeChapterNumber/$pageSrc"
				}
			}

			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				source = source,
				preview = null,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = "$apiUrl/tags".toHttpUrl().newBuilder()
			.build()

		val response = webClient.httpGet(url, apiHeaders).parseJson()
		val tagsArray = response.optJSONArray("resultados")

		if (tagsArray == null) return emptySet()

		return tagsArray.mapJSON { tagJson ->
			MangaTag(
				key = tagJson.getInt("tag_id").toString(),
				title = tagJson.getString("tag_nome").toTitleCase(),
				source = source,
			)
		}.toSet()
	}
}
