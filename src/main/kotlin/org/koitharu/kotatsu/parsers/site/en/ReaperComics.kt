package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet
import kotlin.random.Random

private const val TOO_MANY_REQUESTS = 429
private const val MAX_RETRY_COUNT = 5

@MangaSourceParser("REAPERCOMICS", "ReaperComics", "en")
internal class ReaperComics(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.REAPERCOMICS, pageSize = 32) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.ALPHABETICAL)

	override val configKeyDomain = ConfigKey.Domain("reaperscans.com")

	private val userAgentKey =
		ConfigKey.UserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

	private val baseHeaders: Headers
		get() = Headers.Builder().add("User-Agent", config[userAgentKey]).build()

	override val headers
		get() = getApiHeaders()

	private val selectTotalChapter = "dl.mt-2 div:nth-child(5) > dd"
	private val selectState = "dl.mt-2 div:nth-child(4) > dd"

	private val searchCache = mutableSetOf<Manga>() // Cache search results
	private val chapterCache = mutableMapOf<String, Manga>() // Cache chapter lists

	private val baseUrl = "https://reaperscans.com"

	private fun getApiHeaders(): Headers {
		val userCookie = context.cookieJar.getCookies(domain).find {
			it.name == "user"
		} ?: return baseHeaders
		val jo = JSONObject(userCookie.value.urlDecode())
		val accessToken = jo.getStringOrNull("access_token") ?: return baseHeaders
		return baseHeaders.newBuilder().add("authorization", "bearer $accessToken").build()
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					val searchTitle = filter.query.trim()
					if (searchCache.isNotEmpty()) {
						if (page > 1) {
							return emptyList()
						}
						return searchCache.filter { it.title.contains(searchTitle, ignoreCase = true) }
					} else {
						return searchAllPage(page, searchTitle)
					}
				}

				is MangaListFilter.Advanced -> {
					append("/")
					if (filter.sortOrder == SortOrder.UPDATED) {
						append("latest/")
					}
					append("comics?page=")
					append(page.toString())
				}

				null -> {
					append("/latest/comics?page=")
					append(page.toString())
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	/**
	 * Search once all pages and stores them in cache
	 *
	 * @param page the page to start from
	 * @param searchTitle the title to search for
	 * @return the list of manga
	 */
	private suspend fun searchAllPage(page: Int, searchTitle: String): List<Manga> {
		var currentPage = page
		val url = buildString {
			append("https://")
			append(domain)
			append("/comics?page=")
		}
		while (true) {
			try {
				val allEntries = parseMangaList(webClient.httpGet(url + currentPage).parseHtml())
				if (allEntries.isEmpty()) {
					break
				}
				searchCache.addAll(allEntries)
				currentPage++
			} catch (e: Exception) {
				println("Error parsing page $currentPage: ${e.message}")
				break
			}
		}
		return searchCache.filter { it.title.contains(searchTitle, ignoreCase = true) }.toList()
	}

	/**
	 * Parse the list of manga from the given document
	 *
	 * @param docs the document to parse
	 * @return the list of manga
	 */
	private fun parseMangaList(docs: Document): List<Manga> {
		return docs.select("main div.relative, main li.col-span-1").map {
			val a = it.selectFirstOrThrow("a")
			val url = a.attrAsAbsoluteUrl("href")
			Manga(
				id = generateUid(url),
				url = url,
				title = (it.selectFirst("p a") ?: it.selectLast("a"))?.text().orEmpty(),
				altTitle = null,
				publicUrl = url,
				rating = RATING_UNKNOWN,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirstOrThrow("img").src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	companion object {
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}

	private fun chapterListNextPageSelector(): String = "button[wire:click*=nextPage]"

	private fun chapterListSelector() = "div[wire:id] > div > ul[role=list] > li"

	override suspend fun getDetails(manga: Manga): Manga {
		val cachedChapters = chapterCache[manga.url]
		if (cachedChapters != null) {
			return cachedChapters
		}

		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy", sourceLocale)
		var totalChapters = (doc.selectFirst(selectTotalChapter)?.text()?.toIntOrNull() ?: 0) - 1
		val chapters = mutableSetOf<MangaChapter>()
		var hasNextPage = doc.selectFirst(chapterListNextPageSelector()) != null
		chapters.addAll(
			doc.select(chapterListSelector()).mapChapters { _, li ->
				val a = li.selectFirstOrThrow("a")
				val chapterUrl = a.attr("href").toRelativeUrl(domain)
				MangaChapter(
					id = generateUid(chapterUrl),
					name = li.selectFirst("div.truncate p.truncate")?.text().orEmpty(),
					number = totalChapters--,
					url = chapterUrl,
					scanlator = null,
					uploadDate = parseChapterDate(
						simpleDateFormat,
						li.selectFirst("div.truncate div.items-center")?.text(),
					),
					branch = null,
					source = source,
				)
			},
		)

		if (!hasNextPage) {
			return manga.copy(
				description = doc.selectFirst("div.p-4 p.prose")?.html(),
				state = when (doc.selectFirst(selectState)?.text()?.lowercase()) {
					"ongoing" -> MangaState.ONGOING
					"complete" -> MangaState.FINISHED
					else -> null
				},
				chapters = chapters.reversed(),
			)
		}

		val csrfToken = doc.selectFirst("meta[name=csrf-token]")?.attr("content") ?: error("Couldn't find csrf-token")
		val livewareData = doc.selectFirst("div[wire:initial-data*=Models\\\\Comic]")?.attr("wire:initial-data")
			?.let { JSONObject(it) } ?: error("Couldn't find LiveWireData")

		val routeName =
			livewareData.getJSONObject("fingerprint").getStringOrNull("name") ?: error("Couldn't find routeName")

		val fingerprint = livewareData.getJSONObject("fingerprint")
		var serverMemo = livewareData.getJSONObject("serverMemo")

		var pageToQuery = 2

		//  Javascript: (Math.random() + 1).toString(36).substring(8)
		val generateId = { ->
			"1.${
				Random.nextLong().toString(36)
			}".substring(10)
		} // Not exactly the same, but results in a 3-5 character string

		while (hasNextPage) {
			//need to format the payload to the expected response format since org.json.JSONObject are not ordered, and the server seems to care about the order of the keys
			val payload = String.format(
				responseTemplate,
				fingerprint.getString("id"),
				fingerprint.getString("path"),
				serverMemo.getString("htmlHash"),
				pageToQuery - 1,
				pageToQuery - 1,
				serverMemo.getJSONObject("dataMeta").getJSONObject("models").getJSONObject("comic").getString("id"),
				serverMemo.getString("checksum"),
				generateId(),
				pageToQuery,
			).toRequestBody(JSON_MEDIA_TYPE)

			val headers = Headers.Builder().add("x-csrf-token", csrfToken).add("x-livewire", "true").build()

			val responseData =
				makeRequest("$baseUrl/livewire/message/$routeName", payload, headers)

			// response contains state that we need to preserve
			serverMemo = mergeLeft(serverMemo, responseData.serverMemo)
			val chaptersHtml = Jsoup.parse(responseData.effects.html, baseUrl)
			chapters.addAll(
				chaptersHtml.select(chapterListSelector()).mapChapters { _, li ->
					val a = li.selectFirstOrThrow("a")
					val chapterUrl = a.attr("href").toRelativeUrl(domain)
					MangaChapter(
						id = generateUid(chapterUrl),
						name = li.selectFirst("div.truncate p.truncate")?.text().orEmpty(),
						number = totalChapters--,
						url = chapterUrl,
						scanlator = null,
						uploadDate = parseChapterDate(
							simpleDateFormat,
							li.selectFirst("div.truncate div.items-center")?.text(),
						),
						branch = null,
						source = source,
					)
				},
			)
			hasNextPage = chaptersHtml.selectFirst(chapterListNextPageSelector()) != null
			pageToQuery++
		}

		val copy = manga.copy(
			description = doc.selectFirst("div.p-4 p.prose")?.html(),
			state = when (doc.selectFirst(selectState)?.text()?.lowercase()) {
				"ongoing" -> MangaState.ONGOING
				"complete" -> MangaState.FINISHED
				else -> null
			},
			chapters = chapters.reversed(),
		)

		chapterCache[manga.url] = copy
		return copy

	}

	private suspend fun makeRequest(url: String, payload: RequestBody, headers: Headers): LiveWireResponseDto {
		var retryCount = 0
		val backoffDelay = 2000L // Initial delay (milliseconds)
		val request = Request.Builder().url(url).post(payload).headers(headers).build()

		while (true) {
			try {
				val response = context.httpClient.newCall(request).execute().parseJson()
				val effectsJson = response.getJSONObject("effects")
				val serverMemoJson = response.getJSONObject("serverMemo")
				val effects = LiveWireEffectsDto(effectsJson.getString("html"))
				return LiveWireResponseDto(effects, serverMemoJson)

			} catch (e: Exception) {
				// Log or handle the exception as needed
				if (++retryCount <= MAX_RETRY_COUNT) {
					withContext(Dispatchers.Default) {
						delay(backoffDelay)
					}
				} else {
					throw e
				}
			}
		}
	}

	/**
	 * Recursively merges j2 onto j1 in place
	 * If j1 and j2 both contain keys whose values aren't both jsonObjects, j2's value overwrites j1's
	 *
	 */
	private fun mergeLeft(j1: JSONObject, j2: JSONObject): JSONObject {
		for (key in j2.keys()) {
			val j1Value = j1.opt(key)

			if (j1Value !is JSONObject) {
				j1.put(key, j2[key])
			} else if (j2[key] is JSONObject) {
				j1.put(key, mergeLeft(j1Value, j2.getJSONObject(key)))
			}
		}
		return j1
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
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("img.max-w-full").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}

data class LiveWireResponseDto(
	val effects: LiveWireEffectsDto,
	val serverMemo: JSONObject,
)

data class LiveWireEffectsDto(
	val html: String,
)

//!IMPORTANT
private val responseTemplate =	"""{"fingerprint":{"id":"%s","name":"frontend.comic-chapter-list","locale":"en","path":"%s","method":"GET","v":"acj"},"serverMemo":{"children":[],"errors":[],"htmlHash":"%s","data":{"comic":[],"page":%d,"paginators":{"page":%d}},"dataMeta":{"models":{"comic":{"class":"App\\Models\\Comic","id":"%s","relations":[],"connection":"pgsql","collectionClass":null}}},"checksum":"%s"},"updates":[{"type":"callMethod","payload":{"id":"%s","method":"gotoPage","params":[%d,"page"]}}]}"""
