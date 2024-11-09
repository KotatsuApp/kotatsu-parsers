package org.koitharu.kotatsu.parsers.site.ru.grouple

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
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
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 70
private const val NSFW_ALERT = "сексуальные сцены"
private const val NOTHING_FOUND = "Ничего не найдено"
private const val MIN_IMAGE_SIZE = 1024L
private const val HEADER_ACCEPT = "Accept"
private const val RELATED_TITLE = "Связанные произведения"
private const val NO_CHAPTERS = "В этой манге еще нет ни одной главы"

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
	private val tagsIndex = suspendLazy(initializer = ::fetchTagsMap)

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.add("Accept-Language", "ru,en-US;q=0.7,en;q=0.3")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.RATING,
		SortOrder.ALPHABETICAL,
		SortOrder.ADDED,
	)

	override val authUrl: String
		get() {
			val targetUri = "https://${domain}/".urlEncoded()
			return "https://grouple.co/internal/auth/sso?siteId=$siteId&=targetUri=$targetUri"
		}

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(domain).any { it.name == "gwt" }

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearRangeSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.FINISHED, MangaState.ABANDONED, MangaState.UPCOMING),
	)

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val domain = domain
		val root = if (filter.isEmpty()) {
			webClient.httpGet(
				"https://$domain/list?sortType=${
					getSortKey(order)
				}&offset=${offset upBy PAGE_SIZE}",
			).parseHtml().body().let { doc -> (doc.getElementById("mangaBox") ?: doc.getElementById("mangaResults")) }
		} else {
			advancedSearch(offset, order, filter).parseHtml()
		}
		val tiles =
			root.selectFirst("div.tiles.row") ?: if (root.select(".alert").any { it.ownText() == NOTHING_FOUND }) {
				return emptyList()
			} else {
				root.parseFailed("No tiles found")
			}
		return tiles.select("div.tile").mapNotNull(::parseManga)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val response = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).checkAuthRequired()
		val doc = response.parseHtml()
		val root = doc.body().requireElementById("mangaBox").run {
			selectFirst("div.leftContent") ?: this
		}
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
		val chaptersList = root.getElementById("chapters-list")
		if (chaptersList == null && root.getElementsContainingOwnText(NO_CHAPTERS).isEmpty()) {
			root.parseFailed("No chapters found")
		}
		val hashRegex = Regex("window.user_hash\\s*=\\s*\'([^\']+)\'")
		val userHash = doc.select("script").firstNotNullOfOrNull { it.html().findGroupValue(hashRegex) }
		return manga.copy(
			source = newSource,
			title = doc.metaValue("name") ?: manga.title,
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
			chapters = chaptersList?.select("a.chapter-link")
				?.flatMapChapters(reversed = true) { a ->
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
								url = href.withQueryParam("d", userHash),
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
							val link = href.withQueryParam("tran", personId.toString())
							MangaChapter(
								id = generateUid(link),
								name = a.text().removePrefix(manga.title).trim(),
								number = number,
								volume = volume,
								url = link.withQueryParam("d", userHash),
								uploadDate = dateFormat.tryParse(jo.getStringOrNull("dateCreated")),
								scanlator = null,
								source = newSource,
								branch = translations[personId],
							)
						}
					}
				}.orEmpty(),
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

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
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
		in UsagiParser.domains -> MangaParserSource.USAGI
		else -> source
	}

	private fun getSortKey(sortOrder: SortOrder) = when (sortOrder) {
		SortOrder.ALPHABETICAL -> "name"
		SortOrder.POPULARITY -> "rate"
		SortOrder.UPDATED -> "updated"
		SortOrder.ADDED,
		SortOrder.NEWEST,
			-> "created"

		SortOrder.RATING -> "votes"
		else -> "rate"
	}

	private suspend fun advancedSearch(offset: Int, order: SortOrder, filter: MangaListFilter): Response {
		val tagsMap = tagsIndex.get()
		val url = urlBuilder()
			.addPathSegment("search")
			.addPathSegment("advancedResults")
		url.addQueryParameter("q", filter.query)
		url.addQueryParameter("offset", offset.toString())
		filter.tags.forEach { tag ->
			val tagId = requireNotNull(tagsMap[tag.title.lowercase()]) { "Tag ${tag.title} not found" }
			url.addQueryParameter(tagId, "in")
		}
		filter.tagsExclude.forEach { tag ->
			val tagId = requireNotNull(tagsMap[tag.title.lowercase()]) { "Tag ${tag.title} not found" }
			url.addQueryParameter(tagId, "ex")
		}
		url.addQueryParameter(
			"years",
			buildString {
				append(filter.yearFrom.ifZero { YEAR_MIN })
				append(',')
				append(filter.yearTo.ifZero { YEAR_MAX })
			},
		)
		url.addQueryParameter(
			"sortType",
			when (order) {
				SortOrder.RATING -> "USER_RATING"
				SortOrder.ALPHABETICAL -> "NAME"
				SortOrder.ADDED -> "YEAR"
				SortOrder.POPULARITY -> "POPULARITY"
				SortOrder.NEWEST -> "DATE_CREATE"
				SortOrder.UPDATED -> "DATE_UPDATE"
				else -> "RATING"
			},
		)
		filter.states.forEach { state ->
			when (state) {
				MangaState.FINISHED -> "s_completed"
				MangaState.ABANDONED -> "s_abandoned_popular"
				MangaState.UPCOMING -> "s_wait_upload"
				else -> null
			}?.let {
				url.addQueryParameter(it, "in")
			}
		}

		return webClient.httpGet(url.build())
	}

	private suspend fun tryHead(url: String): Boolean = runCatchingCancellable {
		webClient.httpHead(url).use { response ->
			response.isSuccessful && !response.isPumpkin() && response.headersContentLength() >= MIN_IMAGE_SIZE
		}
	}.getOrDefault(false)

	private fun Response.checkAuthRequired(): Response {
		val lastPathSegment = request.url.pathSegments.lastOrNull() ?: return this
		if (lastPathSegment == "login") {
			closeQuietly()
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

	private suspend fun fetchTagsMap(): ScatterMap<String, String> {
		val url = "https://$domain/search/advanced"
		val properties =
			webClient.httpGet(url).parseHtml().body().selectFirst("form.search-form")?.select("div.form-group")
				?.find { it.selectFirst("li.property") != null }
				?.select("li.property")
				?: throw ParseException("Genres filter element not found", url)
		val result = MutableScatterMap<String, String>(properties.size)
		properties.forEach { li ->
			val name = li.text().lowercase()
			val id = li.selectFirstOrThrow("input").id()
			result[name] = id
		}
		return result
	}

	private fun String.withQueryParam(name: String, value: String?): String {
		if (value == null) return this
		return toAbsoluteUrl(domain)
			.toHttpUrl()
			.newBuilder()
			.setQueryParameter(name, value)
			.build()
			.toString()
			.toRelativeUrl(domain)
	}
}
