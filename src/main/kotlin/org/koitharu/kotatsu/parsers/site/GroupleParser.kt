package org.koitharu.kotatsu.parsers.site

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.json.JSONArray
import org.koitharu.kotatsu.parsers.MangaParser
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

internal abstract class GroupleParser(source: MangaSource, userAgent: String) : MangaParser(source) {

	private val headers = Headers.Builder()
		.add("User-Agent", userAgent)
		.build()

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.RATING,
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		val domain = getDomain()
		val doc = when {
			!query.isNullOrEmpty() -> context.httpPost(
				"https://$domain/search",
				mapOf(
					"q" to query.urlEncoded(),
					"offset" to (offset upBy PAGE_SIZE_SEARCH).toString(),
				),
				headers,
			)
			tags.isNullOrEmpty() -> context.httpGet(
				"https://$domain/list?sortType=${
				getSortKey(
					sortOrder,
				)
				}&offset=${offset upBy PAGE_SIZE}",
				headers,
			)
			tags.size == 1 -> context.httpGet(
				"https://$domain/list/genre/${tags.first().key}?sortType=${
				getSortKey(
					sortOrder,
				)
				}&offset=${offset upBy PAGE_SIZE}",
				headers,
			)
			offset > 0 -> return emptyList()
			else -> advancedSearch(domain, tags)
		}.parseHtml().body()
		val root = (doc.getElementById("mangaBox") ?: doc.getElementById("mangaResults"))
			?: throw ParseException("Cannot find root")
		val tiles = root.selectFirst("div.tiles.row") ?: if (
			root.select(".alert").any { it.ownText() == NOTHING_FOUND }
		) {
			return emptyList()
		} else {
			parseFailed("No tiles found")
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
				coverUrl = imgDiv.selectFirst("img.lazy")?.attr("data-original").orEmpty(),
				rating = runCatching {
					node.selectFirst("div.rating")
						?.attr("title")
						?.substringBefore(' ')
						?.toFloatOrNull()
						?.div(10f)
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
		val doc = context.httpGet(manga.url.withDomain(), headers).parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")
			?: throw ParseException("Cannot find root")
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
			isNsfw = root.select(".alert-warning").any { it.ownText().contains(NSFW_ALERT) },
			chapters = root.selectFirst("div.chapters-link")?.selectFirst("table")
				?.select("tr:has(td > a)")?.asReversed()?.mapIndexedNotNull { i, tr ->
					val a = tr.selectFirst("a") ?: return@mapIndexedNotNull null
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
						uploadDate = dateFormat.tryParse(tr.selectFirst("td.d-none")?.text()),
						scanlator = translators,
						source = source,
						branch = null,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = context.httpGet(chapter.url.withDomain() + "?mtr=1", headers).parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			val pos = data.indexOf("rm_h.initReader(")
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
			val servers = ja.getJSONArray(4).mapJSON { it.getString("path") }
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
		throw ParseException("Pages list not found at ${chapter.url}")
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val parts = page.url.split('|')
		val path = parts.last()
		val servers = parts.dropLast(1).toSet()
		val headers = Headers.headersOf("Referer", page.referer)
		for (server in servers) {
			val url = server + path
			if (tryHead(url, headers)) {
				return url
			}
		}
		val fallbackServer = servers.firstOrNull() ?: parseFailed("Cannot find any page url")
		return fallbackServer + path
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}/list/genres/sort_name", headers).parseHtml()
		val root = doc.body().getElementById("mangaBox")?.selectFirst("div.leftContent")
			?.selectFirst("table.table") ?: parseFailed("Cannot find root")
		return root.select("a.element-link").mapToSet { a ->
			MangaTag(
				title = a.text().toTitleCase(),
				key = a.attr("href").substringAfterLast('/'),
				source = source,
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder?) =
		when (sortOrder ?: sortOrders.minByOrNull { it.ordinal }) {
			SortOrder.ALPHABETICAL -> "name"
			SortOrder.POPULARITY -> "rate"
			SortOrder.UPDATED -> "updated"
			SortOrder.NEWEST -> "created"
			SortOrder.RATING -> "votes"
			null -> "updated"
		}

	private suspend fun advancedSearch(domain: String, tags: Set<MangaTag>): Response {
		val url = "https://$domain/search/advanced"
		// Step 1: map catalog genres names to advanced-search genres ids
		val tagsIndex = context.httpGet(url, headers).parseHtml()
			.body().selectFirst("form.search-form")
			?.select("div.form-group")
			?.get(1) ?: parseFailed("Genres filter element not found")
		val tagNames = tags.map { it.title.lowercase() }
		val payload = HashMap<String, String>()
		var foundGenres = 0
		tagsIndex.select("li.property").forEach { li ->
			val name = li.text().trim().lowercase()
			val id = li.selectFirst("input")?.id()
				?: parseFailed("Id for tag $name not found")
			payload[id] = if (name in tagNames) {
				foundGenres++
				"in"
			} else ""
		}
		if (foundGenres != tags.size) {
			parseFailed("Some genres are not found")
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
		return context.httpPost(url, payload, headers)
	}

	private suspend fun tryHead(url: String, headers: Headers): Boolean = runCatching {
		context.httpHead(url, headers).isSuccessful
	}.getOrDefault(false)
}