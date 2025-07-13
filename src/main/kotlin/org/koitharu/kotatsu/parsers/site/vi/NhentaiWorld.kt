package org.koitharu.kotatsu.parsers.site.vi

import okhttp3.Headers
import okio.ByteString.Companion.encode
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NHENTAIWORLD", "Nhentai World", "vi", ContentType.HENTAI)
internal class NhentaiWorld(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.NHENTAIWORLD, 24) {

	override val configKeyDomain = ConfigKey.Domain("nhentaiworld-h1.art")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("origin", "https://$domain")
		.add("referer", "https://$domain")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isMultipleTagsSupported = false,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val urlBuilder = urlBuilder()
			.addPathSegment("genre")
		filter.tags.oneOrThrowIfMany()?.also {
			urlBuilder.addPathSegment(it.key)
		} ?: urlBuilder.addPathSegment("all")
		urlBuilder.addQueryParameter(
			"sort",
			when (order) {
				SortOrder.UPDATED -> "recent-update"
				SortOrder.POPULARITY -> "view"
				else -> "recent-update"
			},
		)
		filter.query?.nullIfEmpty()?.let {
			urlBuilder.addQueryParameter("search", it)
		}

		filter.states.oneOrThrowIfMany()?.let {
			urlBuilder.addQueryParameter(
				"status",
				when (it) {
					MangaState.ONGOING -> "progress"
					MangaState.FINISHED -> "completed"
					else -> ""
				},
			)
		}

		urlBuilder.addQueryParameter("page", page.toString())

		val doc = webClient.httpGet(urlBuilder.build()).parseHtml()
		return doc.select("div.relative.mb-1.h-full.max-h-\\[375px\\]").map { div ->
			val img = div.selectFirst("img.hover\\:scale-105.transition-all.w-full.h-full")
			val a = div.selectFirstOrThrow("a")

			val title = img?.attr("alt").orEmpty()
			val coverUrl = img?.attrAsAbsoluteUrlOrNull("src")
			val href = a.attrAsRelativeUrl("href")

			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = ContentRating.ADULT,
				coverUrl = coverUrl,
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.selectFirst("div.flex-1.bg-neutral-900") ?: return manga
		val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
			timeZone = TimeZone.getTimeZone("GMT+7")
		}

		val tags = root.select("div.flex.flex-wrap.gap-2 button").mapNotNullToSet { button ->
			val tagName = button.text().toTitleCase(sourceLocale)
			val tagUrl = button.parent()?.attrOrNull("href")?.substringAfterLast('/')
			if (tagUrl != null) {
				MangaTag(title = tagName, key = tagUrl, source = source)
			} else {
				null
			}
		}

		val state = when {
			root.selectFirst("a[href*='status=completed']") != null -> MangaState.FINISHED
			root.selectFirst("a[href*='status=progress']") != null -> MangaState.ONGOING
			else -> null
		}

		val description = root.selectFirst("div#introduction-wrap p.font-light")?.html()?.nullIfEmpty()

		val altTitles = description?.split("\n")?.mapNotNullToSet { line ->
			when {
				line.startsWith("Tên tiếng anh:", ignoreCase = true) ->
					line.substringAfter(':').substringBefore("Tên gốc:").trim()

				line.startsWith("Tên gốc:", ignoreCase = true) ->
					line.substringAfter(':').trim().substringBefore(' ')

				else -> null
			}
		}

		val scriptTag = doc.select("script").firstOrNull { script ->
			val data = script.data()
			data.contains("data") && data.contains("chapterListEn")
		}?.data()
		val chapters = parseChapterList(scriptTag, manga, chapterDateFormat)

		return manga.copy(
			title = doc.selectFirst("h1")!!.text(),
			tags = tags,
			state = state,
			description = description,
			altTitles = altTitles.orEmpty(),
			chapters = chapters.reversed(),
		)
	}

	private fun parseChapterList(scriptTag: String?, manga: Manga, chapterDateFormat: SimpleDateFormat): List<MangaChapter> {
		val idManga = manga.url.substringAfter("detail/").toIntOrNull() ?: return emptyList()

		val chapters = ArrayList<MangaChapter>()
		if (scriptTag.isNullOrEmpty()) return chapters

		val cleanedScript = scriptTag.replace("\\", "")

		val cutScript = "null,{\"data\""
		val needScript = cleanedScript.indexOf(cutScript)
		if (needScript == -1) return chapters
		val finalScript = cleanedScript.substring(needScript)

		val vnPrefix = "null,{\"data\":"
		val vnStart = finalScript.indexOf(vnPrefix)
		if (vnStart == -1) return chapters
		val beforeEn = ",\"chapterListEn\""
		val vnEnd = finalScript.indexOf(beforeEn, vnStart)
		if (vnEnd == -1) return chapters
		val vnChapterStr = finalScript.substring(vnStart + vnPrefix.length, vnEnd)

		val vnArray = try {
			JSONArray(vnChapterStr)
		} catch (e: Exception) {
			JSONArray()
		}

		for (i in 0 until vnArray.length()) {
			val chapter = vnArray.getJSONObject(i)
			val name = chapter.optString("name", null) ?: continue
			val uploadDateStr = chapter.optString("createdAt", null)
			val uploadDate = chapterDateFormat.tryParse(uploadDateStr)
			val href = "${idManga}/${name}?lang=VI"
			chapters.add(
				MangaChapter(
					id = generateUid(href),
					title = if (name.toFloatOrNull() != null) "Chapter $name" else name,
					number = name.toFloatOrNull() ?: (i + 1).toFloat(),
					url = "/read/${href}",
					scanlator = null,
					uploadDate = uploadDate,
					branch = "Tiếng Việt",
					source = source,
					volume = 0
				)
			)
		}

		// Copy + Paste from VI
		val enPrefix = ",\"chapterListEn\":"
		val enStart = finalScript.indexOf(enPrefix)
		if (enStart == -1) return chapters
		val beforeId = ",\"id\""
		val enEnd = finalScript.indexOf(beforeId, enStart)
		if (enEnd == -1) return chapters
		val enChapterStr = finalScript.substring(enStart + enPrefix.length, enEnd)

		val enArray = try {
			JSONArray(enChapterStr)
		} catch (e: Exception) {
			JSONArray()
		}

		for (i in 0 until enArray.length()) {
			val chapter = enArray.getJSONObject(i)
			val name = chapter.optString("name", null) ?: continue
			val uploadDateStr = chapter.optString("createdAt", null)
			val uploadDate = chapterDateFormat.tryParse(uploadDateStr)
			val href = "${idManga}/${name}?lang=EN"
			chapters.add(
				MangaChapter(
					id = generateUid(href),
					title = if (name.toFloatOrNull() != null) "Chapter $name" else name,
					number = name.toFloatOrNull() ?: (i + 1).toFloat(),
					url = "/read/${href}",
					scanlator = null,
					uploadDate = uploadDate,
					branch = "English",
					source = source,
					volume = 0
				)
			)
		}

		return chapters
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.select("img.m-auto.read-image.w-auto.h-auto.md\\:min-h-\\[800px\\].min-h-\\[300px\\]")

		if (root.isEmpty()) { // for Debug #1604
			throw ParseException("Root not found!", url)
		}

		return root.map { img ->
			val imgUrl = img.requireSrc()
			MangaPage(
				id = generateUid(imgUrl),
				url = imgUrl,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain").parseHtml()
		val scriptSrc = doc.select("script")[7].src()!!
		val docJS = webClient.httpGet(scriptSrc).parseRaw()

		val optionsStart = docJS.indexOf("genres:[{")
		if (optionsStart == -1) return emptySet()

		val optionsEnd = docJS.indexOf("}]", optionsStart)
		if (optionsEnd == -1) return emptySet()

		val optionsStr = docJS.substring(optionsStart + 7, optionsEnd + 2)

		val optionsArray = JSONArray(
			optionsStr
				.replace(Regex(",description:\\s*\"[^\"]*\"(,?)"), "")
				.replace(Regex("(\\w+):"), "\"$1\":")
		)

		return buildSet {
			for (i in 0 until optionsArray.length()) {
				// {"label":"Ahegao","href":"/genre/ahegao"}
				val option = optionsArray.getJSONObject(i)
				val title = option.getStringOrNull("label")!!.toTitleCase(sourceLocale)
				val key = option.getStringOrNull("href")!!.split("/")[2]
				if (title.isNotEmpty() && key.isNotEmpty()) {
					if (title != "Tất cả" || key != "all") { // remove "All" tags, default list = all
						add(MangaTag(title = title, key = key, source = source))
					}
				}
			}
		}
	}
}
