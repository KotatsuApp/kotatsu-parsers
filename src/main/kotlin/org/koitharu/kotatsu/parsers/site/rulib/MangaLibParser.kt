package org.koitharu.kotatsu.parsers.site.rulib

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.ChaptersListBuilder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.json.JSONIterator
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.parseJsonArray
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

internal open class MangaLibParser(
	context: MangaLoaderContext,
	source: MangaSource,
) : PagedMangaParser(context, source, pageSize = 60), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("mangalib.me")

	override val authUrl: String
		get() = "https://${domain}/login"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.RATING,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			return if (page == searchPaginator.firstPage) search(query) else emptyList()
		}
		val url = buildString {
			append("https://")
			append(domain)
			append("/manga-list?dir=")
			append(getSortKey(sortOrder))
			append("&page=")
			append(page)
			tags?.forEach { tag ->
				append("&genres[include][]=")
				append(tag.key)
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().getElementById("manga-list") ?: doc.parseFailed("Root not found")
		val items = root.selectFirst("div.media-cards-grid")?.select("div.media-card-wrap") ?: return emptyList()
		return items.mapNotNull { card ->
			val a = card.selectFirst("a.media-card") ?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = card.selectFirst("h3")?.text().orEmpty(),
				coverUrl = a.absUrl("data-src"),
				altTitle = null,
				author = null,
				rating = RATING_UNKNOWN,
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				tags = emptySet(),
				state = null,
				isNsfw = false,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet("$fullUrl?section=info").parseHtml()
		val root = doc.body().getElementById("main-page") ?: throw ParseException("Root not found", fullUrl)
		val title = root.selectFirst("div.media-header__wrap")?.children()
		val info = root.selectFirst("div.media-content")
		val chaptersDoc = webClient.httpGet("$fullUrl?section=chapters").parseHtml()
		val scripts = chaptersDoc.select("script")
		val dateFormat = SimpleDateFormat("yyy-MM-dd", Locale.US)
		var chapters: ChaptersListBuilder? = null
		scripts@ for (script in scripts) {
			val raw = script.html().lines()
			for (line in raw) {
				if (line.startsWith("window.__DATA__")) {
					val json = JSONObject(line.substringAfter('=').substringBeforeLast(';'))
					val list = json.getJSONObject("chapters").getJSONArray("list")
					val id = json.optJSONObject("user")?.getLong("id")?.toString() ?: "not"
					val total = list.length()
					chapters = ChaptersListBuilder(total)
					for (i in 0 until total) {
						val item = list.getJSONObject(i)
						val chapterId = item.getLong("chapter_id")
						val scanlator = item.getStringOrNull("username")
						val url = buildString {
							if (isAuthorized) {
								append(manga.url)
								append("/v")
								append(item.getInt("chapter_volume"))
								append("/c")
								append(item.getString("chapter_number"))
								append("?ui=")
								append(id)
							} else {
								append(manga.url)
								append("/v")
								append(item.getInt("chapter_volume"))
								append("/c")
								append(item.getString("chapter_number"))
							}
						}
						val nameChapter = item.getStringOrNull("chapter_name")
						val volume = item.getInt("chapter_volume")
						val number = item.getString("chapter_number")
						val fullNameChapter = "Том $volume. Глава $number"
						chapters.add(
							MangaChapter(
								id = generateUid(chapterId),
								url = url,
								source = source,
								number = total - i,
								uploadDate = dateFormat.tryParse(
									item.getString("chapter_created_at").substringBefore(" "),
								),
								scanlator = scanlator,
								branch = null,
								name = if (nameChapter.isNullOrBlank()) fullNameChapter else "$fullNameChapter - $nameChapter",
							),
						)
					}
					chapters.reverse()
					break@scripts
				}
			}
		}
		return manga.copy(
			title = title?.getOrNull(0)?.text()?.takeUnless(String::isBlank) ?: manga.title,
			altTitle = title?.getOrNull(1)?.text()?.substringBefore('/')?.trim(),
			rating = root.selectFirst("div.media-stats-item__score")?.selectFirst("span")?.text()?.toFloatOrNull()
				?.div(5f) ?: manga.rating,
			author = info?.getElementsMatchingOwnText("Автор")?.firstOrNull()?.nextElementSibling()?.text()
				?: manga.author,
			tags = info?.selectFirst("div.media-tags")?.select("a.media-tag-item")?.mapNotNullToSet { a ->
				val href = a.attr("href")
				if (href.contains("genres")) {
					MangaTag(
						title = a.text().toTitleCase(),
						key = href.substringAfterLast('='),
						source = source,
					)
				} else null
			} ?: manga.tags,
			isNsfw = isNsfw(doc),
			description = info?.selectFirst("div.media-description__text")?.html(),
			chapters = chapters?.toList(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = handle404 {
			webClient.httpGet(fullUrl).parseHtml()
		}
		if (doc.location().substringBefore('?').endsWith("/register")) {
			throw AuthRequiredException(source)
		}
		val scripts = doc.head().select("script")
		val pg =
			(doc.body().getElementById("pg")?.html() ?: doc.parseFailed("Element #pg not found")).substringAfter('=')
				.substringBeforeLast(';')
		val pages = JSONArray(pg)
		for (script in scripts) {
			val raw = script.html().trim()
			if (raw.contains("window.__info")) {
				val json = JSONObject(
					raw.substringAfter("window.__info").substringAfter('=').substringBeforeLast(';'),
				)
				val domain = json.getJSONObject("servers").run {
					getStringOrNull("main") ?: getString(
						json.getJSONObject("img").getString("server"),
					)
				}
				val url = json.getJSONObject("img").getString("url")
				return pages.mapJSON { x ->
					val pageUrl = "$domain/$url${x.getString("u")}"
					MangaPage(
						id = generateUid(pageUrl),
						url = pageUrl,
						preview = null,
						source = source,
					)
				}
			}
		}
		throw ParseException("Script with info not found", fullUrl)
	}

	override suspend fun getTags(): Set<MangaTag> {
		val url = "https://${domain}/manga-list"
		val doc = webClient.httpGet(url).parseHtml()
		val scripts = doc.body().select("script")
		for (script in scripts) {
			val raw = script.html().trim()
			if (raw.startsWith("window.__DATA")) {
				val json = JSONObject(raw.substringAfter('=').substringBeforeLast(';'))
				val genres = json.getJSONObject("filters").getJSONArray("genres")
				val result = ArraySet<MangaTag>(genres.length())
				for (x in genres.JSONIterator()) {
					result += MangaTag(
						source = source,
						key = x.getInt("id").toString(),
						title = x.getString("name").toTitleCase(),
					)
				}
				return result
			}
		}
		throw ParseException("Script with genres not found", url)
	}

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name.startsWith("remember_web_")
			}
		}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${LIB_SOCIAL_LINK}/messages").parseHtml().body()
		if (body.baseUri().endsWith("/login")) {
			throw AuthRequiredException(source)
		}
		return body.selectFirst(".profile-user__username")?.text() ?: body.parseFailed("Cannot find username")
	}

	protected open fun isNsfw(doc: Document): Boolean {
		val modal = doc.body().getElementById("title-caution")
		if (!modal?.getElementsContainingOwnText("18+").isNullOrEmpty()) {
			return true
		}
		val sidebar = doc.body().run {
			selectFirst(".media-sidebar") ?: selectFirst(".media-info")
		} ?: doc.parseFailed("Sidebar not found")
		return sidebar.getElementsContainingOwnText("18+").isNotEmpty()
	}

	private fun getSortKey(sortOrder: SortOrder?) = when (sortOrder) {
		SortOrder.RATING -> "desc&sort=rate"
		SortOrder.ALPHABETICAL -> "asc&sort=name"
		SortOrder.POPULARITY -> "desc&sort=views"
		SortOrder.UPDATED -> "desc&sort=last_chapter_at"
		SortOrder.NEWEST -> "desc&sort=created_at"
		else -> "desc&sort=last_chapter_at"
	}

	private suspend fun search(query: String): List<Manga> {
		val domain = domain
		val json = webClient.httpGet("https://$domain/search?type=manga&q=$query").parseJsonArray()
		return json.mapJSON { jo ->
			val slug = jo.getString("slug")
			val url = "/$slug"
			val covers = jo.getJSONObject("covers")
			val title = jo.getString("rus_name").ifEmpty { jo.getString("name") }
			Manga(
				id = generateUid(url),
				url = url,
				publicUrl = "https://$domain/$slug",
				title = title,
				altTitle = jo.getString("name").takeUnless { it == title },
				author = null,
				tags = emptySet(),
				rating = jo.getString("rate_avg").toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				state = null,
				isNsfw = false,
				source = source,
				coverUrl = covers.getString("thumbnail").toAbsoluteUrl(domain),
				largeCoverUrl = covers.getString("default").toAbsoluteUrl(domain),
			)
		}
	}

	private inline fun <T> handle404(block: () -> T): T = try {
		block()
	} catch (e: NotFoundException) {
		if (isAuthorized) {
			throw e
		} else {
			throw AuthRequiredException(source)
		}
	}

	@MangaSourceParser("MANGALIB", "MangaLib", "ru")
	class Impl(context: MangaLoaderContext) : MangaLibParser(context, MangaSource.MANGALIB)

	companion object {

		const val LIB_SOCIAL_LINK = "lib.social"
	}
}
