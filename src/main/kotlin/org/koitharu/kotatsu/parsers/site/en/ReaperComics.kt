package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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
private const val MAX_RETRY_COUNT = 3

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

	private inline fun <reified T> Response.parseJson(): T = use {
		it.body!!.string().parseJson()
	}

	private inline fun <reified T> String.parseJson(): T = json.decodeFromString(this)

	companion object {
		private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}

	private fun chapterListNextPageSelector(): String = "button[wire:click*=nextPage]"

	private val json = Json {
		ignoreUnknownKeys = true
	}

	private fun chapterListSelector() = "div[wire:id] > div > ul[role=list] > li"

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = Jsoup.parse(webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseRaw())
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
			?.parseJson<LiveWireDataDto>() ?: error("Couldn't find LiveWireData")

		val routeName =
			livewareData.fingerprint["name"]?.jsonPrimitive?.contentOrNull ?: error("Couldn't find routeName")

		val fingerprint = livewareData.fingerprint
		var serverMemo = livewareData.serverMemo

		var pageToQuery = 2

		//  Javascript: (Math.random() + 1).toString(36).substring(8)
		val generateId = { ->
			"1.${
				Random.nextLong().toString(36)
			}".substring(10)
		} // Not exactly the same, but results in a 3-5 character string

		while (hasNextPage) {
			val payload = buildJsonObject {
				put("fingerprint", fingerprint)
				put("serverMemo", serverMemo)
				putJsonArray("updates") {
					addJsonObject {
						put("type", "callMethod")
						putJsonObject("payload") {
							put("id", generateId())
							put("method", "gotoPage")
							putJsonArray("params") {
								add(pageToQuery)
								add("page")
							}
						}
					}
				}
			}.toString().toRequestBody(JSON_MEDIA_TYPE)

			val headers = Headers.Builder().add("x-csrf-token", csrfToken).add("x-livewire", "true").build()

			val responseData = makeRequest("$baseUrl/livewire/message/$routeName", payload, headers)

			// response contains state that we need to preserve
			serverMemo = serverMemo.mergeLeft(responseData.serverMemo)
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

	private suspend fun makeRequest(url: String, payload: RequestBody, headers: Headers): LiveWireResponseDto {
		var retryCount = 0
		var backoffDelay = 2000L // Initial delay (milliseconds)
		val request = Request.Builder().url(url).post(payload).headers(headers).build()
		while (true) {
			try {
				return context.httpClient.newCall(request).execute().parseJson<LiveWireResponseDto>()
			} catch (e: Exception) {
				// Log or handle the exception as needed
				if (++retryCount <= MAX_RETRY_COUNT) {
					withContext(Dispatchers.Default) {
						delay(backoffDelay)
						backoffDelay += 500L
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
	private fun JsonObject.mergeLeft(j2: JsonObject): JsonObject = buildJsonObject {
		val j1 = this@mergeLeft
		j1.entries.forEach { (key, value) -> put(key, value) }
		j2.entries.forEach { (key, value) ->
			val j1Value = j1[key]
			when {
				j1Value !is JsonObject -> put(key, value)
				value is JsonObject -> put(key, j1Value.mergeLeft(value))
			}
		}
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

@Serializable
data class LiveWireResponseDto(
	val effects: LiveWireEffectsDto,
	val serverMemo: JsonObject,
)

@Serializable
data class LiveWireEffectsDto(
	val html: String,
)

@Serializable
data class LiveWireDataDto(
	val fingerprint: JsonObject,
	val serverMemo: JsonObject,
)
