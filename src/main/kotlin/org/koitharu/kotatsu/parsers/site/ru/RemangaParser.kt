package org.koitharu.kotatsu.parsers.site.ru

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ContentUnavailableException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 30
private const val STATUS_ONGOING = 1
private const val STATUS_FINISHED = 0
private const val TOO_MANY_REQUESTS = 429

@MangaSourceParser("REMANGA", "Реманга", "ru")
internal class RemangaParser(
	context: MangaLoaderContext,
) : PagedMangaParser(context, MangaParserSource.REMANGA, PAGE_SIZE), MangaParserAuthProvider, Interceptor {

	private val baseHeaders: Headers
		get() = Headers.Builder()
			.add("User-Agent", config[userAgentKey])
			.build()

	override fun getRequestHeaders() = getApiHeaders()

	override val configKeyDomain = ConfigKey.Domain("remanga.org", "реманга.орг")

	override val authUrl: String
		get() = "https://${domain}"

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.RATING,
		SortOrder.NEWEST,
	)

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name == "user"
			}
		}

	private val regexLastUrlPath = Regex("/[^/]+/?$")

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.code == TOO_MANY_REQUESTS) {
			response.closeQuietly()
			Thread.sleep(1000)
			return chain.proceed(chain.request().newBuilder().build())
		}
		return response
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		copyCookies()
		val domain = domain
		val urlBuilder = StringBuilder()
			.append("https://api.")
			.append(domain)
		if (!filter.query.isNullOrEmpty()) {
			urlBuilder.append("/api/search/?query=")
				.append(filter.query.urlEncoded())
		} else {
			urlBuilder.append("/api/search/catalog/?ordering=")
				.append(getSortKey(order))
			filter.tags.forEach { tag ->
				urlBuilder.append("&genres=")
				urlBuilder.append(tag.key)
			}
		}
		urlBuilder
			.append("&page=")
			.append(page)
			.append("&count=")
			.append(PAGE_SIZE)
		val content = webClient.httpGet(urlBuilder.toString()).parseJson()
			.getJSONArray("content")
		return content.mapJSON { jo ->
			val url = "/manga/${jo.getString("dir")}"
			val img = jo.getJSONObject("img")
			Manga(
				id = generateUid(url),
				url = url,
				publicUrl = "https://$domain$url",
				title = jo.getString("rus_name"),
				altTitle = jo.getString("en_name"),
				rating = jo.getString("avg_rating").toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				coverUrl = "https://api.$domain${img.getString("mid")}",
				largeCoverUrl = "https://api.$domain${img.getString("high")}",
				author = null,
				isNsfw = false,
				state = null,
				tags = jo.optJSONArray("genres")?.mapJSONToSet { g ->
					MangaTag(
						title = g.getString("name").toTitleCase(),
						key = g.getInt("id").toString(),
						source = MangaParserSource.REMANGA,
					)
				}.orEmpty(),
				source = MangaParserSource.REMANGA,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		copyCookies()
		val domain = domain
		val slug = manga.url.find(regexLastUrlPath)
			?: throw ParseException("Cannot obtain slug from ${manga.url}", manga.publicUrl)
		val data = webClient.httpGet(
			url = "https://api.$domain/api/titles$slug/",
		).parseJson()
		val content = try {
			data.getJSONObject("content")
		} catch (e: JSONException) {
			throw ParseException(data.optString("msg"), manga.publicUrl, e)
		}
		val branchId = content.getJSONArray("branches").optJSONObject(0)
			?.getLong("id") ?: throw ParseException("No branches found", manga.publicUrl)
		val chapters = grabChapters(domain, branchId)
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
		return manga.copy(
			description = content.getString("description"),
			state = when (content.optJSONObject("status")?.getInt("id")) {
				STATUS_ONGOING -> MangaState.ONGOING
				STATUS_FINISHED -> MangaState.FINISHED
				else -> null
			},
			tags = content.getJSONArray("genres").mapJSONToSet { g ->
				MangaTag(
					title = g.getString("name").toTitleCase(),
					key = g.getInt("id").toString(),
					source = MangaParserSource.REMANGA,
				)
			},
			chapters = chapters.mapChapters { i, jo ->
				if (
					jo.getBooleanOrDefault("is_paid", false) &&
					!jo.getBooleanOrDefault("is_bought", false)
				) {
					return@mapChapters null
				}
				val id = jo.getLong("id")
				val name = jo.getString("name").toTitleCase(Locale.ROOT)
				val publishers = jo.optJSONArray("publishers")
				MangaChapter(
					id = generateUid(id),
					url = "/api/titles/chapters/$id/",
					number = jo.getIntOrDefault("index", chapters.size - i).toFloat(),
					volume = 0,
					name = buildString {
						append("Том ")
						append(jo.optString("tome", "0"))
						append(". ")
						append("Глава ")
						append(jo.optString("chapter", "0"))
						if (name.isNotEmpty()) {
							append(" - ")
							append(name)
						}
					},
					uploadDate = dateFormat.tryParse(jo.getString("upload_date")),
					scanlator = publishers?.optJSONObject(0)?.getStringOrNull("name"),
					source = MangaParserSource.REMANGA,
					branch = null,
				)
			}.asReversed(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val content = webClient.httpGet(chapter.url.toAbsoluteUrl(getDomain("api")))
			.parseJson()
			.getJSONObject("content")
		val pages = content.optJSONArray("pages")
		if (pages == null) {
			val pubDate = content.getStringOrNull("pub_date")?.let {
				SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).tryParse(it)
			}
			if (pubDate != null && pubDate > System.currentTimeMillis()) {
				val at = SimpleDateFormat.getDateInstance(DateFormat.LONG).format(Date(pubDate))
				throw ContentUnavailableException("Глава станет доступной $at")
			} else {
				throw ContentUnavailableException("Глава недоступна")
			}
		}
		val result = ArrayList<MangaPage>(pages.length())
		for (i in 0 until pages.length()) {
			when (val item = pages.get(i)) {
				is JSONObject -> result += parsePage(item)
				is JSONArray -> item.mapJSONTo(result) { parsePage(it) }
				else -> throw ParseException("Unknown json item $item", chapter.url)
			}
		}
		return result
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val domain = domain
		val content = webClient.httpGet("https://api.$domain/api/forms/titles/?get=genres")
			.parseJson().getJSONObject("content").getJSONArray("genres")
		return content.mapJSONToSet { jo ->
			MangaTag(
				title = jo.getString("name").toTitleCase(),
				key = jo.getInt("id").toString(),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val jo = webClient.httpGet(
			url = "https://api.${domain}/api/users/current/",
		).parseJson()
		return jo.getJSONObject("content").getString("username")
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private fun getApiHeaders(): Headers {
		val userCookie = context.cookieJar.getCookies(domain).find {
			it.name == "user"
		} ?: return baseHeaders
		val jo = JSONObject(userCookie.value.urlDecode())
		val accessToken = jo.getStringOrNull("access_token") ?: return baseHeaders
		return baseHeaders.newBuilder().add("authorization", "bearer $accessToken").build()
	}

	private fun copyCookies() {
		val domain = domain
		context.cookieJar.copyCookies(domain, "api.$domain")
	}

	private fun getSortKey(order: SortOrder?) = when (order) {
		SortOrder.UPDATED -> "-chapter_date"
		SortOrder.POPULARITY -> "-rating"
		SortOrder.RATING -> "-votes"
		SortOrder.NEWEST -> "-id"
		else -> "-chapter_date"
	}

	private fun parsePage(jo: JSONObject) = MangaPage(
		// id = generateUid(jo.getLong("id")), 19.01.2024 page id is gone
		id = generateUid(jo.getString("link")),
		url = jo.getString("link"),
		preview = null,
		source = source,
	)

	private suspend fun grabChapters(domain: String, branchId: Long): List<JSONObject> {
		val result = ArrayList<JSONObject>(100)
		var page = 1
		while (true) {
			val content = webClient.httpGet(
				url = "https://api.$domain/api/titles/chapters/?branch_id=$branchId&page=$page&count=500",
			).parseJson().getJSONArray("content")
			val len = content.length()
			if (len == 0) {
				break
			}
			result.ensureCapacity(result.size + len)
			for (i in 0 until len) {
				result.add(content.getJSONObject(i))
			}
			page++
		}
		return result
	}
}
