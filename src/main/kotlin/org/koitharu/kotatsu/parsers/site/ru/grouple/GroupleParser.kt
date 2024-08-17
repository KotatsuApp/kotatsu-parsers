package org.koitharu.kotatsu.parsers.site.ru.grouple

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.headersContentLength
import org.json.JSONArray
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 70
private const val PAGE_SIZE_SEARCH = 50
private const val NSFW_ALERT = "сексуальные сцены"
private const val NOTHING_FOUND = "Ничего не найдено"
private const val MIN_IMAGE_SIZE = 1024L
private const val HEADER_ACCEPT = "Accept"
private const val RELATED_TITLE = "Связанные произведения"

internal abstract class GroupleParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	private val siteId: Int,
) : MangaParser(context, source), MangaParserAuthProvider, Interceptor {

	@Volatile
	private var cachedPagesServer: String? = null
	protected open val defaultIsNsfw = false

	override val userAgentKey = ConfigKey.UserAgent(
		"Mozilla/5.0 (X11; U; UNICOS lcLinux; en-US) Gecko/20140730 (KHTML, like Gecko, Safari/419.3) Arora/0.8.0",
	)
	private val splitTranslationsKey = ConfigKey.SplitByTranslations(false)

	override fun getRequestHeaders(): Headers = Headers.Builder().add("User-Agent", config[userAgentKey]).build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
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

	override suspend fun getList(offset: Int, filter: MangaListFilter?): List<Manga> {
		val domain = domain
		val doc = when (filter) {
			is MangaListFilter.Search -> webClient.httpPost(
				"https://$domain/search",
				mapOf(
					"q" to filter.query.urlEncoded(),
					"offset" to (offset upBy PAGE_SIZE_SEARCH).toString(),
					"fast-filter" to "CREATION",
				),
			)

			null -> webClient.httpGet(
				"https://$domain/list?sortType=${
					getSortKey(defaultSortOrder)
				}&offset=${offset upBy PAGE_SIZE}",
			)

			is MangaListFilter.Advanced -> when {
				filter.tags.isEmpty() -> webClient.httpGet(
					"https://$domain/list?sortType=${
						getSortKey(filter.sortOrder)
					}&offset=${offset upBy PAGE_SIZE}",
				)

				filter.tags.size == 1 -> webClient.httpGet(
					"https://$domain/list/genre/${filter.tags.first().key}?sortType=${
						getSortKey(filter.sortOrder)
					}&offset=${offset upBy PAGE_SIZE}",
				)

				offset > 0 -> return emptyList()
				else -> advancedSearch(domain, filter.tags)
			}
		}.parseHtml().body()
		val root = (doc.getElementById("mangaBox") ?: doc.getElementById("mangaResults"))
			?: doc.parseFailed("Cannot find root")
		val tiles =
			root.selectFirst("div.tiles.row") ?: if (root.select(".alert").any { it.ownText() == NOTHING_FOUND }) {
				return emptyList()
			} else {
				doc.parseFailed("No tiles found")
			}
		return tiles.select("div.tile").mapNotNull(::parseManga)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val response = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).checkAuthRequired()
		val doc = response.parseHtml()
		val root = doc.body().requireElementById("mangaBox").selectFirstOrThrow("div.leftContent")
		val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.US)
		val coverImg = root.selectFirst("div.subject-cover")?.selectFirst("img")
		val translations = if (config[splitTranslationsKey]) {
			root.selectFirst("div.translator-selection")
				?.select(".translator-selection-item")
				?.associate {
					it.id().removePrefix("tr-").toLong() to it.selectFirst(".translator-selection-name")?.textOrNull()
				}
		} else {
			null
		}
		val newSource = getSource(response.request.url)
		return manga.copy(
			source = newSource,
			altTitle = root.selectFirst(".all-names-popover")?.select(".name")?.joinToString { it.text() }
				?: manga.altTitle,
			publicUrl = response.request.url.toString(),
			description = root.selectFirst("div.manga-description")?.html(),
			largeCoverUrl = coverImg?.attr("data-full"),
			coverUrl = coverImg?.attr("data-thumb") ?: manga.coverUrl,
			tags = root.selectFirstOrThrow("div.subject-meta")
				.getElementsByAttributeValueContaining("href", "/list/genre/").mapTo(manga.tags.toMutableSet()) { a ->
					MangaTag(
						title = a.text().toTitleCase(),
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						source = source,
					)
				},
			author = root.selectFirst("a.person-link")?.text() ?: manga.author,
			isNsfw = manga.isNsfw || root.select(".alert-warning").any { it.ownText().contains(NSFW_ALERT) },
			chapters = root.requireElementById("chapters-list").select("a.chapter-link")
				.flatMapChapters(reversed = true) { a ->
					val tr = a.selectFirstParent("tr") ?: return@flatMapChapters emptyList()
					val href = a.attrAsRelativeUrl("href")
					val number = tr.attr("data-num").toFloatOrNull()?.div(10f) ?: 0f
					val volume = tr.attr("data-vol").toIntOrNull() ?: 0
					if (translations.isNullOrEmpty() || a.attr("data-translations").isEmpty()) {
						var translators = ""
						val translatorElement = a.attr("title")
						if (!translatorElement.isNullOrBlank()) {
							translators = translatorElement.replace("(Переводчик),", "&").removeSuffix(" (Переводчик)")
						}
						listOf(
							MangaChapter(
								id = generateUid(href),
								name = a.text().removePrefix(manga.title).trim(),
								number = number,
								volume = volume,
								url = href,
								uploadDate = dateFormat.tryParse(tr.selectFirst("td.date")?.text()),
								scanlator = translators,
								source = newSource,
								branch = null,
							),
						)
					} else {
						val translationData = JSONArray(a.attr("data-translations"))
						translationData.mapJSON { jo ->
							val personId = jo.getLong("personId")
							val link = href.setQueryParam("tran", personId.toString())
							MangaChapter(
								id = generateUid(link),
								name = a.text().removePrefix(manga.title).trim(),
								number = number,
								volume = volume,
								url = link,
								uploadDate = dateFormat.tryParse(jo.getStringOrNull("dateCreated")),
								scanlator = null,
								source = newSource,
								branch = translations[personId],
							)
						}
					}
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		if (chapter.source != source && chapter.source is MangaParserSource) { // handle redirects between websites
			return context.newParserInstance(chapter.source).getPages(chapter)
		}
		val url = chapter.url.toAbsoluteUrl(domain).toHttpUrl().newBuilder().setQueryParameter("mtr", "1").build()
		val doc = webClient.httpGet(url).checkAuthRequired().parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			var pos = data.indexOf("rm_h.readerDoInit(")
			if (pos != -1) {
				parsePagesNew(data, pos)?.let { return it }
			}
			pos = data.indexOf("rm_h.readerInit( 0,")
			if (pos != -1) {
				parsePagesOld(data, pos)?.let { return it }
			}
		}
		doc.parseFailed("Pages list not found at ${chapter.url}")
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val parts = page.url.split('|')
		if (parts.size < 2) {
			throw ParseException("No servers found for page", page.url)
		}
		val path = parts.last()
		// fast path
		cachedPagesServer?.let { host ->
			val url = concatUrl("https://$host/", path)
			if (tryHead(url)) {
				return url
			} else {
				cachedPagesServer = null
			}
		}
		// slow path
		val candidates = HashSet<String>((parts.size - 1) * 2)
		for (i in 0 until parts.size - 1) {
			val server = parts[i].trim().ifEmpty { "https://$domain/" }
			candidates.add(concatUrl(server, path))
			candidates.add(concatUrl(server, path.substringBeforeLast('?')))
		}
		return try {
			channelFlow {
				for (url in candidates) {
					launch {
						if (tryHead(url)) {
							send(url)
						}
					}
				}
			}.first().also {
				cachedPagesServer = it.toHttpUrlOrNull()?.host
			}
		} catch (e: NoSuchElementException) {
			candidates.randomOrNull() ?: throw ParseException("No page url candidates", page.url, e)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/list/genres/sort_name").parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")?.selectFirst("table.table")
			?: doc.parseFailed("Cannot find root")
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

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		if (!request.header(HEADER_ACCEPT).isNullOrEmpty()) {
			return chain.proceed(request)
		}
		val ext = request.url.pathSegments.lastOrNull()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
		return if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp") {
			chain.proceed(
				request.newBuilder().header(HEADER_ACCEPT, "image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8").build(),
			)
		} else {
			chain.proceed(request)
		}
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(splitTranslationsKey)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).checkAuthRequired().parseHtml()
		val root = doc.body().requireElementById("mangaBox").select("h4").first { it.ownText() == RELATED_TITLE }
			.nextElementSibling() ?: doc.parseFailed("Cannot find root")
		return root.select("div.tile").mapNotNull(::parseManga)
	}

	protected open fun getSource(url: HttpUrl): MangaSource = when (url.host) {
		in SeiMangaParser.domains -> MangaParserSource.SEIMANGA
		in MintMangaParser.domains -> MangaParserSource.MINTMANGA
		in ReadmangaParser.domains -> MangaParserSource.READMANGA_RU
		in SelfMangaParser.domains -> MangaParserSource.SELFMANGA
		else -> source
	}

	private fun getSortKey(sortOrder: SortOrder) = when (sortOrder) {
		SortOrder.ALPHABETICAL -> "name"
		SortOrder.POPULARITY -> "rate"
		SortOrder.UPDATED -> "updated"
		SortOrder.NEWEST -> "created"
		SortOrder.RATING -> "votes"
		else -> null
	}

	private suspend fun advancedSearch(domain: String, tags: Set<MangaTag>): Response {
		val url = "https://$domain/search/advanced"
		// Step 1: map catalog genres names to advanced-search genres ids
		val tagsIndex =
			webClient.httpGet(url).parseHtml().body().selectFirst("form.search-form")?.select("div.form-group")
				?.find { it.selectFirst("li.property") != null }
				?: throw ParseException("Genres filter element not found", url)
		val tagNames = tags.map { it.title.lowercase() }
		val payload = HashMap<String, String>()
		var foundGenres = 0
		tagsIndex.select("li.property").forEach { li ->
			val name = li.text().trim().lowercase()
			val id = li.selectFirst("input")?.id() ?: li.parseFailed("Id for tag $name not found")
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

	private suspend fun tryHead(url: String): Boolean = runCatchingCancellable {
		webClient.httpHead(url).use { response ->
			response.isSuccessful && !response.isPumpkin() && response.headersContentLength() >= MIN_IMAGE_SIZE
		}
	}.getOrDefault(false)

	private fun Response.checkAuthRequired(): Response {
		val lastPathSegment = request.url.pathSegments.lastOrNull() ?: return this
		if (lastPathSegment == "login") {
			throw AuthRequiredException(source)
		}
		return this
	}

	private fun Response.isPumpkin(): Boolean = request.url.host == "upload.wikimedia.org"

	private fun parseManga(node: Element): Manga? {
		val imgDiv = node.selectFirst("div.img") ?: return null
		val descDiv = node.selectFirst("div.desc") ?: return null
		if (descDiv.selectFirst("i.fa-user") != null || descDiv.selectFirst("i.fa-external-link") != null) {
			return null // skip author
		}
		val href = imgDiv.selectFirst("a")?.attrAsAbsoluteUrlOrNull("href") ?: return null
		val title = descDiv.selectFirst("h3")?.selectFirst("a")?.text() ?: return null
		val tileInfo = descDiv.selectFirst("div.tile-info")
		val relUrl = href.toRelativeUrl(domain)
		if (relUrl.contains("://")) {
			return null
		}
		return Manga(
			id = generateUid(relUrl),
			url = relUrl,
			publicUrl = href,
			title = title,
			altTitle = descDiv.selectFirst("h5")?.textOrNull(),
			coverUrl = imgDiv.selectFirst("img.lazy")?.attr("data-original")?.replace("_p.", ".").orEmpty(),
			rating = runCatching {
				node.selectFirst(".compact-rate")?.attr("title")?.toFloatOrNull()?.div(5f)
			}.getOrNull() ?: RATING_UNKNOWN,
			author = tileInfo?.selectFirst("a.person-link")?.text(),
			isNsfw = defaultIsNsfw,
			tags = runCatching {
				tileInfo?.select("a.element-link")?.mapToSet {
					MangaTag(
						title = it.text().toTitleCase(),
						key = it.attr("href").substringAfterLast('/'),
						source = source,
					)
				}
			}.getOrNull().orEmpty(),
			state = when {
				node.selectFirst("div.tags")?.selectFirst("span.mangaCompleted") != null -> MangaState.FINISHED

				else -> null
			},
			source = source,
		)
	}

	private fun parsePagesNew(data: String, pos: Int): List<MangaPage>? {
		val json = data.substring(pos).substringAfter('(').substringBefore('\n').substringBeforeLast(')')
		if (json.isEmpty()) {
			return null
		}
		val ja = JSONArray("[$json]")
		val pages = ja.getJSONArray(0)
		val servers = ja.getJSONArray(2).mapJSON { it.getString("path") }
		val serversStr = servers.joinToString("|")
		return (0 until pages.length()).map { i ->
			val page = pages.getJSONArray(i)
			val primaryServer = page.getString(0)
			val url = page.getString(2)
			MangaPage(
				id = generateUid(url),
				url = "$primaryServer|$serversStr|$url",
				preview = null,
				source = source,
			)
		}
	}

	private fun parsePagesOld(data: String, pos: Int): List<MangaPage>? {
		val json = data.substring(pos).substringAfter('(').substringBefore('\n').substringBeforeLast(')')
		if (json.isEmpty()) {
			return null
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
				source = source,
			)
		}
	}

	private fun String.setQueryParam(name: String, value: String): String {
		return toAbsoluteUrl(domain)
			.toHttpUrl()
			.newBuilder()
			.setQueryParameter(name, value)
			.build()
			.toString()
			.toRelativeUrl(domain)
	}
}
