package org.koitharu.kotatsu.parsers.site.ru

import okhttp3.Headers
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ZENMANGA", "ZenManga", "ru")
internal class ZenMangaParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ZENMANGA, 30),
	MangaParserAuthProvider {

	private val astroJsonParser = AstroJsonParser()
	private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

	init {
		setFirstPage(0)
	}

	override val configKeyDomain = ConfigKey.Domain("v1.zenmanga.one", "v1.zenmanga.me")

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC
	)

	override val filterCapabilities: MangaListFilterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
		isMultipleTagsSupported = true,
		isTagsExclusionSupported = true,
		isYearRangeSupported = true,
		isSearchWithFiltersSupported = true,
		isAuthorSearchSupported = true,
	)

	override val authUrl: String
		get() = "https://sso.inuko.me/account/sign-in"

	private fun checkAuth(): Boolean {
		val authCookieName = "__otaku_session"
		return context.cookieJar.getCookies("v1.zenmanga.one").any { it.name == authCookieName } ||
			context.cookieJar.getCookies("v1.zenmanga.me").any { it.name == authCookieName }
	}

	override suspend fun isAuthorized(): Boolean {
		return checkAuth()
	}

	override suspend fun getUsername(): String {
		val libraryUrl = "/library"
		val data = fetchAstroData(libraryUrl)
			?: throw ParseException("Не удалось получить Astro JSON для получения имени пользователя", libraryUrl)

		val session = data["session"] as? Map<*, *>
			?: throw ParseException("Ключ 'session' не найден", libraryUrl)

		val currentUser = session["currentUser"] as? Map<*, *>
			?: throw ParseException("Ключ 'currentUser' не найден", libraryUrl)

		return currentUser["username"] as? String
			?: throw ParseException("Ключ 'username' не найден", libraryUrl)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (!filter.author.isNullOrBlank()) {
			return getListPageByAuthor(filter.author, page)
		}

		val apiDomain = domain.replace("v1.", "api.")
		val urlBuilder = HttpUrl.Builder()
			.scheme("https")
			.host(apiDomain)
			.addPathSegment("v2")
			.addPathSegment("books")

		urlBuilder.addQueryParameter("page", page.toString())
		urlBuilder.addQueryParameter("size", pageSize.toString())

		urlBuilder.addQueryParameter("sort", getSortParameter(order))

		if (!filter.query.isNullOrBlank()) {
			urlBuilder.addQueryParameter("search", filter.query)
		}

		filter.tags.forEach { tag ->
			urlBuilder.addQueryParameter("labelsInclude", tag.key)
		}

		filter.tagsExclude.forEach { tag ->
			urlBuilder.addQueryParameter("labelsExclude", tag.key)
		}

		filter.states.forEach { state ->
			urlBuilder.addQueryParameter("status", when(state) {
				MangaState.ONGOING -> "ONGOING"
				MangaState.FINISHED -> "DONE"
				MangaState.PAUSED -> "FROZEN"
				MangaState.UPCOMING -> "ANNOUNCE"
				else -> ""
			})
		}

		filter.contentRating.forEach { rating ->
			urlBuilder.addQueryParameter("contentStatus", when(rating) {
				ContentRating.SAFE -> "SAFE"
				ContentRating.SUGGESTIVE -> "UNSAFE"
				ContentRating.ADULT -> "EROTIC"
			})
		}

		if (filter.yearFrom != YEAR_UNKNOWN) {
			urlBuilder.addQueryParameter("yearMin", filter.yearFrom.toString())
		}
		if (filter.yearTo != YEAR_UNKNOWN) {
			urlBuilder.addQueryParameter("yearMax", filter.yearTo.toString())
		}

		val requestUrl = urlBuilder.build()
		val response = webClient.httpGet(requestUrl).parseJsonArray()

		return response.mapJSON { parseMangaFromJson(it) }
	}

	private suspend fun getListPageByAuthor(authorQuery: String, page: Int): List<Manga> {
		val apiDomain = domain.replace("v1.", "api.")

		val authorSearchUrl = HttpUrl.Builder()
			.scheme("https")
			.host(apiDomain)
			.addPathSegment("v2")
			.addPathSegment("publishers")
			.addQueryParameter("search", authorQuery)
			.build()

		val publishersResponse = webClient.httpGet(authorSearchUrl).parseJsonArray()

		var authorId: String? = null
		for (i in 0 until publishersResponse.length()) {
			val publisher = publishersResponse.getJSONObject(i)
			if (publisher.getStringOrNull("kind") == "AUTHOR") {
				authorId = publisher.getStringOrNull("id")
				break
			}
		}

		if (authorId == null) {
			return emptyList()
		}

		val booksByAuthorUrl = HttpUrl.Builder()
			.scheme("https")
			.host(apiDomain)
			.addPathSegment("v2")
			.addPathSegment("books")
			.addQueryParameter("publisherId", authorId)
			.addQueryParameter("page", page.toString())
			.addQueryParameter("size", pageSize.toString())
			.addQueryParameter("sort", "createdAt,desc")
			.build()

		val booksResponse = webClient.httpGet(booksByAuthorUrl).parseJsonArray()

		return booksResponse.mapJSON { parseMangaFromJson(it) }
	}

	private fun parseMangaFromJson(json: JSONObject): Manga {
		val slug = json.getString("slug")
		val nameObj = json.getJSONObject("name")
		val title = nameObj.getStringOrNull("ru") ?: nameObj.getString("en")

		val publicUrl = "https://$domain/content/$slug"

		val altNames = json.getJSONArray("altNames")
			.mapJSON { it.getString("name") }
			.toSet()

		return Manga(
			id = generateUid(publicUrl),
			url = "/content/$slug",
			publicUrl = "https://$domain/content/$slug",
			title = title,
			altTitles = altNames,
			coverUrl = json.getStringOrNull("poster"),
			source = source,
			rating = json.getDouble("averageRating").toFloat() / 10f,
			state = when (json.getStringOrNull("status")) {
				"ONGOING" -> MangaState.ONGOING
				"DONE" -> MangaState.FINISHED
				"FROZEN" -> MangaState.PAUSED
				"ANNOUNCE" -> MangaState.UPCOMING
				else -> null
			},
			contentRating = when (json.getStringOrNull("contentStatus")) {
				"SAFE" -> ContentRating.SAFE
				"UNSAFE" -> ContentRating.SUGGESTIVE
				"EROTIC" -> ContentRating.ADULT
				else -> null
			},
			tags = emptySet(),
			authors = emptySet()
		)
	}

	private fun getSortParameter(order: SortOrder): String {
		val field = when (order) {
			SortOrder.POPULARITY, SortOrder.POPULARITY_ASC -> "viewsCount"
			SortOrder.RATING, SortOrder.RATING_ASC -> "averageRating"
			SortOrder.NEWEST, SortOrder.NEWEST_ASC -> "createdAt"
			else -> "viewsCount"
		}
		val direction = when (order) {
			SortOrder.POPULARITY_ASC, SortOrder.RATING_ASC, SortOrder.NEWEST_ASC -> "asc"
			else -> "desc"
		}
		return "$field,$direction"
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val data = fetchAstroData(manga.url)
			?: throw ParseException("Не удалось получить Astro JSON для деталей манги", manga.publicUrl)

		val bookData = data["current-book"] as? Map<*, *> ?: return manga
		val branchesData = data["current-book-branches"] as? List<Map<*, *>> ?: emptyList()
		val chaptersData = data["current-book-chapters"] as? List<Map<*, *>> ?: emptyList()

		val description = bookData["description"] as? String

		val tags = (bookData["labels"] as? List<Map<*, *>>)?.mapNotNullTo(HashSet()) {
			val tagName = it["name"] as? String
			val tagKey = it["slug"] as? String
			if (tagName != null && tagKey != null) {
				MangaTag(key = tagKey, title = tagName.replaceFirstChar { c -> c.uppercase() }, source = source)
			} else null
		} ?: emptySet()

		val authors = (bookData["relations"] as? List<Map<*, *>>)?.mapNotNullTo(HashSet()) {
			val type = it["type"] as? String
			if (type == "AUTHOR" || type == "ARTIST") {
				(it["publisher"] as? Map<*, *>)?.get("name") as? String
			} else null
		} ?: emptySet()

		val branchIdToNameMap = branchesData.associate { branchMap ->
			val branchId = branchMap["id"] as? String

			val scanlatorNames = (branchMap["publishers"] as? List<Map<*, *>>)
				?.mapNotNull { publisherMap -> publisherMap["name"] as? String }
				?.joinToString(" & ")

			branchId to scanlatorNames
		}

		val slug = manga.url.substringAfterLast('/')

		val chapters = chaptersData.mapNotNull { chapterMap ->
			val id = chapterMap["id"] as? String ?: return@mapNotNull null
			val branchId = chapterMap["branchId"] as? String
			val scanlator = branchIdToNameMap[branchId]

			MangaChapter(
				id = generateUid(id),
				url = "/content/$slug/$id",
				title = chapterMap["name"] as? String,
				number = chapterMap["number"].toSafeFloat(),
				volume = chapterMap["volume"].toSafeInt(),
				uploadDate = dateFormat.parseSafe(chapterMap["createdAt"] as? String),
				scanlator = scanlator,
				branch = scanlator,
				source = source
			)
		}.reversed()

		return manga.copy(
			description = description,
			tags = manga.tags + tags,
			authors = authors,
			chapters = chapters
		)
	}

	override fun getRequestHeaders() = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_DESKTOP)
		.build()

	private fun Any?.toSafeInt(): Int {
		return when(this) {
			is Number -> this.toInt()
			is String -> this.toIntOrNull() ?: 0
			else -> 0
		}
	}

	private fun Any?.toSafeFloat(): Float {
		return when(this) {
			is Number -> this.toFloat()
			is String -> this.toFloatOrNull() ?: 0f
			else -> 0f
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val data = fetchAstroData(chapter.url)
			?: throw ParseException("Не удалось получить Astro JSON для страниц главы", chapter.url)

		val chapterData = data["reader-current-chapter"] as? Map<*, *>
			?: throw ParseException("Ключ 'reader-current-chapter' не найден", chapter.url)

		val pagesList = chapterData["pages"] as? List<Map<*, *>>
			?: throw ParseException("Список страниц 'pages' не найден", chapter.url)

		return pagesList
			.sortedBy { it["index"].toSafeInt() }
			.mapNotNull { pageMap ->
				val id = pageMap["id"] as? String
				val imageUrl = pageMap["image"] as? String
				if (id == null || imageUrl == null) return@mapNotNull null

				MangaPage(
					id = generateUid(id),
					url = "$imageUrl?width=1600",
					preview = null,
					source = source
				)
			}
	}

	private suspend fun fetchAstroData(relativeUrl: String): Map<*, *>? {
		val fullUrl = relativeUrl.toAbsoluteUrl(domain)

		val response = webClient.httpGet(fullUrl)

		val protection = CloudFlareHelper.checkResponseForProtection(response.copy())
		if (protection != CloudFlareHelper.PROTECTION_NOT_DETECTED) {
			response.close()
			context.requestBrowserAction(this, fullUrl)
			return null
		}

		val responseHtml = response.parseHtml()
		val scriptElement = responseHtml.getElementById("it-astro-state")
			?: throw ParseException("Не удалось найти <script id='it-astro-state'> на странице $fullUrl.", fullUrl)

		return astroJsonParser.parse(scriptElement.data())
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = allGenres.toSet(),
			availableStates = allStates,
			availableContentRating = allContentRatings
		)
	}

	private val allGenres: List<MangaTag> = listOf(
		MangaTag(key = "art", title = "Арт", source = source),
		MangaTag(key = "martial_arts", title = "Боевые искусства", source = source),
		MangaTag(key = "vampires", title = "Вампиры", source = source),
		MangaTag(key = "harem", title = "Гарем", source = source),
		MangaTag(key = "gender_intriga", title = "Гендерная интрига", source = source),
		MangaTag(key = "detective", title = "Детектив", source = source),
		MangaTag(key = "josei", title = "Дзёсэй", source = source),
		MangaTag(key = "game", title = "Игра", source = source),
		MangaTag(key = "cyberpunk", title = "Киберпанк", source = source),
		MangaTag(key = "maho_shoujo", title = "Махо-сёдзё", source = source),
		MangaTag(key = "mecha", title = "Меха", source = source),
		MangaTag(key = "mystery", title = "Мистика", source = source),
		MangaTag(key = "sci_fi", title = "Научная фантастика", source = source),
		MangaTag(key = "natural", title = "Повседневность", source = source),
		MangaTag(key = "postapocalypse", title = "Постапокалипсис", source = source),
		MangaTag(key = "adventure", title = "Приключения", source = source),
		MangaTag(key = "psychological", title = "Психология", source = source),
		MangaTag(key = "samurai", title = "Самураи", source = source),
		MangaTag(key = "supernatural", title = "Сверхъестественное", source = source),
		MangaTag(key = "sports", title = "Спорт", source = source),
		MangaTag(key = "seinen", title = "Сэйнэн", source = source),
		MangaTag(key = "thriller", title = "Триллер", source = source),
		MangaTag(key = "horror", title = "Ужасы", source = source),
		MangaTag(key = "fantastic", title = "Фантастика", source = source),
		MangaTag(key = "fantasy", title = "Фэнтези", source = source),
		MangaTag(key = "school", title = "Школа", source = source),
		MangaTag(key = "erotica", title = "Эротика", source = source),
		MangaTag(key = "ecchi", title = "Этти", source = source),
		MangaTag(key = "codomo", title = "Кодомо", source = source),
		MangaTag(key = "isekai", title = "Исекай", source = source),
		MangaTag(key = "omegavers", title = "Омегаверс", source = source),
		MangaTag(key = "comedy", title = "Комедия", source = source),
		MangaTag(key = "shounen", title = "Сёнэн", source = source),
		MangaTag(key = "romance", title = "Романтика", source = source),
		MangaTag(key = "drama", title = "Драма", source = source),
		MangaTag(key = "shoujo", title = "Сёдзё", source = source),
		MangaTag(key = "historical", title = "История", source = source),
		MangaTag(key = "tragedy", title = "Трагедия", source = source),
		MangaTag(key = "action", title = "Боевик", source = source)
	).sortedBy { it.title }

	private val allStates: Set<MangaState> = EnumSet.of(
		MangaState.ONGOING,
		MangaState.FINISHED,
		MangaState.PAUSED,
		MangaState.UPCOMING
	)

	private val allContentRatings: Set<ContentRating> = EnumSet.of(
		ContentRating.SAFE,
		ContentRating.SUGGESTIVE,
		ContentRating.ADULT
	)

	private class AstroJsonParser {
		fun parse(compressedJson: String): Map<*, *>? {
			return try {
				val rootArray = JSONArray(compressedJson)
				if (rootArray.length() == 0) return emptyMap<Any, Any>()

				val cache = mutableMapOf<Int, Any?>()
				val overdueMap = decompress(rootArray.get(0), rootArray, cache) as? Map<*, *> ?: emptyMap<Any, Any>()

				return overdueMap["@inox-tools/request-nanostores"] as? Map<*, *> ?: emptyMap<Any, Any>()
			} catch (e: Exception) {
				e.printStackTrace()
				null
			}
		}

		private fun decompress(value: Any?, rootArray: JSONArray, cache: MutableMap<Int, Any?>): Any? {
			if (value is Int) {
				val ref = value
				if (cache.containsKey(ref)) return cache[ref]
				if (ref < 0 || ref >= rootArray.length()) return ref

				cache[ref] = null
				val referencedItem = rootArray.get(ref)
				val result = processItem(referencedItem, rootArray, cache)
				cache[ref] = result
				return result
			}
			return processItem(value, rootArray, cache)
		}

		private fun processItem(item: Any?, rootArray: JSONArray, cache: MutableMap<Int, Any?>): Any? {
			return when (item) {
				is JSONObject -> {
					val map = mutableMapOf<String, Any?>()
					for (key in item.keys()) {
						map[key] = decompress(item.get(key), rootArray, cache)
					}
					map
				}
				is JSONArray -> {
					if (item.length() > 0 && item.get(0) is String) {
						when (item.getString(0)) {
							"Map" -> {
								val map = mutableMapOf<Any?, Any?>()
								for (i in 1 until item.length() step 2) {
									val key = decompress(item.get(i), rootArray, cache)
									val value = decompress(item.get(i + 1), rootArray, cache)
									if (key != null) map[key] = value
								}
								return map
							}
							"URL" -> return if (item.length() > 1) decompress(item.get(1), rootArray, cache) else null
						}
					}
					(0 until item.length()).map { i -> decompress(item.get(i), rootArray, cache) }
				}
				else -> item
			}
		}
	}
}
