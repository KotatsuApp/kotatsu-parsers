package org.koitharu.kotatsu.parsers.site.grouple

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.internal.headersContentLength
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 70
private const val PAGE_SIZE_SEARCH = 50
private const val NSFW_ALERT = "сексуальные сцены"
private const val NOTHING_FOUND = "Ничего не найдено"
private const val MIN_IMAGE_SIZE = 1024L

internal abstract class GroupleParser(
	context: MangaLoaderContext,
	source: MangaSource,
	private val siteId: Int,
) : MangaParser(context, source), MangaParserAuthProvider {

	@Volatile
	private var cachedPagesServer: String? = null

	private val userAgentKey = ConfigKey.UserAgent(
		"Mozilla/5.0 (X11; U; UNICOS lcLinux; en-US) Gecko/20140730 (KHTML, like Gecko, Safari/419.3) Arora/0.8.0",
	)

	override val headers: Headers
		get() = Headers.Builder()
			.add("User-Agent", config[userAgentKey])
			.build()

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.RATING,
	)

	override val authUrl: String
		get() {
			val targetUri = "https://${domain}/".urlEncoded()
			return "https://grouple.co/internal/auth/sso?siteId=$siteId&=targetUri=$targetUri"
		}

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(domain).any { it.name == "gwt" }

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val domain = domain
		val doc = when {
			!query.isNullOrEmpty() -> webClient.httpPost(
				"https://$domain/search",
				mapOf(
					"q" to query.urlEncoded(),
					"offset" to (offset upBy PAGE_SIZE_SEARCH).toString(),
				),
			)

			tags.isNullOrEmpty() -> webClient.httpGet(
				"https://$domain/list?sortType=${
					getSortKey(sortOrder)
				}&offset=${offset upBy PAGE_SIZE}",
			)

			tags.size == 1 -> webClient.httpGet(
				"https://$domain/list/genre/${tags.first().key}?sortType=${
					getSortKey(sortOrder)
				}&offset=${offset upBy PAGE_SIZE}",
			)

			offset > 0 -> return emptyList()
			else -> advancedSearch(domain, tags)
		}.parseHtml().body()
		val root = (doc.getElementById("mangaBox") ?: doc.getElementById("mangaResults"))
			?: doc.parseFailed("Cannot find root")
		val tiles = root.selectFirst("div.tiles.row") ?: if (
			root.select(".alert").any { it.ownText() == NOTHING_FOUND }
		) {
			return emptyList()
		} else {
			doc.parseFailed("No tiles found")
		}
		val baseHost = root.baseUri().toHttpUrl().host
		return tiles.select("div.tile").mapNotNull { node ->
			val imgDiv = node.selectFirst("div.img") ?: return@mapNotNull null
			val descDiv = node.selectFirst("div.desc") ?: return@mapNotNull null
			if (descDiv.selectFirst("i.fa-user") != null) {
				return@mapNotNull null // skip author
			}
			val href = imgDiv.selectFirst("a")?.attrAsAbsoluteUrlOrNull("href")
			if (href == null || href.toHttpUrl().host != baseHost) {
				return@mapNotNull null // skip external links
			}
			val title = descDiv.selectFirst("h3")?.selectFirst("a")?.text()
				?: return@mapNotNull null
			val tileInfo = descDiv.selectFirst("div.tile-info")
			val relUrl = href.toRelativeUrl(baseHost)
			Manga(
				id = generateUid(relUrl),
				url = relUrl,
				publicUrl = href,
				title = title,
				altTitle = descDiv.selectFirst("h4")?.text(),
				coverUrl = imgDiv.selectFirst("img.lazy")?.attr("data-original")?.replace("_p.", ".").orEmpty(),
				rating = runCatching {
					node.selectFirst(".compact-rate")
						?.attr("title")
						?.toFloatOrNull()
						?.div(5f)
				}.getOrNull() ?: RATING_UNKNOWN,
				author = tileInfo?.selectFirst("a.person-link")?.text(),
				isNsfw = false,
				tags = runCatching {
					tileInfo?.select("a.element-link")
						?.mapToSet {
							MangaTag(
								title = it.text().toTitleCase(),
								key = it.attr("href").substringAfterLast('/'),
								source = source,
							)
						}
				}.getOrNull().orEmpty(),
				state = when {
					node.selectFirst("div.tags")
						?.selectFirst("span.mangaCompleted") != null -> MangaState.FINISHED

					else -> null
				},
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).checkAuthRequired().parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")
			?: doc.parseFailed("Cannot find root")
		val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.US)
		val coverImg = root.selectFirst("div.subject-cover")?.selectFirst("img")
		return manga.copy(
			description = root.selectFirst("div.manga-description")?.html(),
			largeCoverUrl = coverImg?.attr("data-full"),
			coverUrl = coverImg?.attr("data-thumb") ?: manga.coverUrl,
			tags = manga.tags + root.select("div.subject-meta").select("span.elem_genre ")
				.mapNotNull {
					val a = it.selectFirst("a.element-link") ?: return@mapNotNull null
					MangaTag(
						title = a.text().toTitleCase(),
						key = a.attr("href").substringAfterLast('/'),
						source = source,
					)
				},
			author = root.selectFirst("a.person-link")?.text() ?: manga.author,
			isNsfw = root.select(".alert-warning").any { it.ownText().contains(NSFW_ALERT) },
			chapters = root.selectFirst("div.chapters-link")?.selectFirst("table")
				?.select("tr:has(td > a)")?.asReversed()?.mapChapters { i, tr ->
					val a = tr.selectFirst("a.chapter-link") ?: return@mapChapters null
					val href = a.attrAsRelativeUrl("href")
					var translators = ""
					val translatorElement = a.attr("title")
					if (!translatorElement.isNullOrBlank()) {
						translators = translatorElement
							.replace("(Переводчик),", "&")
							.removeSuffix(" (Переводчик)")
					}
					MangaChapter(
						id = generateUid(href),
						name = tr.selectFirst("a")?.text().orEmpty().removePrefix(manga.title).trim(),
						number = i + 1,
						url = href,
						uploadDate = dateFormat.tryParse(tr.selectFirst("td.date")?.text()),
						scanlator = translators,
						source = source,
						branch = null,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain) + "?mtr=1")
			.checkAuthRequired()
			.parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			val pos = data.indexOf("rm_h.readerInit( 0,")
			if (pos == -1) {
				continue
			}
			val json = data.substring(pos)
				.substringAfter('(')
				.substringBefore('\n')
				.substringBeforeLast(')')
			if (json.isEmpty()) {
				continue
			}
			val ja = JSONArray("[$json]")
			val pages = ja.getJSONArray(1)
			val servers = ja.getJSONArray(3).mapJSON { it.getString("path") }
			val serversStr = servers.joinToString("|")
			return (0 until pages.length()).map { i ->
				val page = pages.getJSONArray(i)
				val primaryServer = page.getString(0)
				val url = page.getString(2)
				MangaPage(
					id = generateUid(url),
					url = "$primaryServer|$serversStr|$url",
					preview = null,
					referer = chapter.url,
					source = source,
				)
			}
		}
		doc.parseFailed("Pages list not found at ${chapter.url}")
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val parts = page.url.split('|')
		val path = parts.last()
		val servers = parts.dropLast(1).toSet()
		val cachedServer = cachedPagesServer
		if (cachedServer != null && cachedServer in servers && tryHead(cachedServer + path)) {
			return cachedServer + path
		}
		if (servers.isEmpty()) {
			throw ParseException("No servers found for page", page.url)
		}
		val server = try {
			coroutineScope {
				servers.map { server ->
					async {
						if (tryHead(server + path)) server else null
					}
				}.awaitFirst { it != null }
			}.also {
				cachedPagesServer = it
			}
		} catch (e: NoSuchElementException) {
			servers.random()
		}
		return checkNotNull(server) + path
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/list/genres/sort_name").parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")
			?.selectFirst("table.table") ?: doc.parseFailed("Cannot find root")
		return root.select("a.element-link").mapToSet { a ->
			MangaTag(
				title = a.text().toTitleCase(),
				key = a.attr("href").substringAfterLast('/'),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val root = webClient.httpGet("https://grouple.co/").parseHtml().body()
		val element = root.selectFirst("img.user-avatar") ?: throw AuthRequiredException(source)
		val res = element.parent()?.text()
		return if (res.isNullOrEmpty()) {
			root.parseFailed("Cannot find username")
		} else res
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "rate"
			SortOrder.UPDATED -> "updated"
			SortOrder.NEWEST -> "created"
			SortOrder.RATING -> "votes"
		}

	private suspend fun advancedSearch(domain: String, tags: Set<MangaTag>): Response {
		val url = "https://$domain/search/advanced"
		// Step 1: map catalog genres names to advanced-search genres ids
		val tagsIndex = webClient.httpGet(url).parseHtml()
			.body().selectFirst("form.search-form")
			?.select("div.form-group")
			?.get(1) ?: throw ParseException("Genres filter element not found", url)
		val tagNames = tags.map { it.title.lowercase() }
		val payload = HashMap<String, String>()
		var foundGenres = 0
		tagsIndex.select("li.property").forEach { li ->
			val name = li.text().trim().lowercase()
			val id = li.selectFirst("input")?.id()
				?: li.parseFailed("Id for tag $name not found")
			payload[id] = if (name in tagNames) {
				foundGenres++
				"in"
			} else ""
		}
		if (foundGenres != tags.size) {
			tagsIndex.parseFailed("Some genres are not found")
		}
		// Step 2: advanced search
		payload["q"] = ""
		payload["s_high_rate"] = ""
		payload["s_single"] = ""
		payload["s_mature"] = ""
		payload["s_completed"] = ""
		payload["s_translated"] = ""
		payload["s_many_chapters"] = ""
		payload["s_wait_upload"] = ""
		payload["s_sale"] = ""
		payload["years"] = "1900,2099"
		payload["+"] = "Искать".urlEncoded()
		return webClient.httpPost(url, payload)
	}

	suspend fun tryHead(url: String): Boolean = runCatchingCancellable {
		val response = webClient.httpHead(url)
		response.isSuccessful && response.headersContentLength() >= MIN_IMAGE_SIZE
	}.getOrDefault(false)

	private fun Response.checkAuthRequired(): Response {
		val lastPathSegment = request.url.pathSegments.lastOrNull() ?: return this
		if (lastPathSegment == "login") {
			throw AuthRequiredException(source)
		}
		return this
	}
}
