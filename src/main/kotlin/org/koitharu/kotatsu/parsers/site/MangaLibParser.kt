package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.JSONIterator
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

internal open class MangaLibParser(
	override val context: MangaLoaderContext,
	source: MangaSource,
) : MangaParser(source), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain("mangalib.me", null)

	override val authUrl: String
		get() = "https://${getDomain()}/login"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.RATING,
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			return if (offset == 0) search(query) else emptyList()
		}
		val page = (offset / 60f).toIntUp()
		val url = buildString {
			append("https://")
			append(getDomain())
			append("/manga-list?dir=")
			append(getSortKey(sortOrder))
			append("&page=")
			append(page)
			tags?.forEach { tag ->
				append("&genres[include][]=")
				append(tag.key)
			}
		}
		val doc = context.httpGet(url).parseHtml()
		val root = doc.body().getElementById("manga-list") ?: throw ParseException("Root not found")
		val items = root.selectFirst("div.media-cards-grid")?.select("div.media-card-wrap")
			?: return emptyList()
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
				publicUrl = href.toAbsoluteUrl(a.host ?: getDomain()),
				tags = emptySet(),
				state = null,
				isNsfw = false,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet("$fullUrl?section=info").parseHtml()
		val root = doc.body().getElementById("main-page") ?: throw ParseException("Root not found")
		val title = root.selectFirst("div.media-header__wrap")?.children()
		val info = root.selectFirst("div.media-content")
		val chaptersDoc = context.httpGet("$fullUrl?section=chapters").parseHtml()
		val scripts = chaptersDoc.select("script")
		val dateFormat = SimpleDateFormat("yyy-MM-dd", Locale.US)
		var chapters: ChaptersListBuilder? = null
		scripts@ for (script in scripts) {
			val raw = script.html().lines()
			for (line in raw) {
				if (line.startsWith("window.__DATA__")) {
					val json = JSONObject(line.substringAfter('=').substringBeforeLast(';'))
					val list = json.getJSONObject("chapters").getJSONArray("list")
					val total = list.length()
					chapters = ChaptersListBuilder(total)
					for (i in 0 until total) {
						val item = list.getJSONObject(i)
						val chapterId = item.getLong("chapter_id")
						val scanlator = item.getStringOrNull("username")
						val url = buildString {
							append(manga.url)
							append("/v")
							append(item.getInt("chapter_volume"))
							append("/c")
							append(item.getString("chapter_number"))
							append('/')
							append(item.optString("chapter_string"))
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
			rating = root.selectFirst("div.media-stats-item__score")
				?.selectFirst("span")
				?.text()?.toFloatOrNull()?.div(5f) ?: manga.rating,
			author = info?.getElementsMatchingOwnText("Автор")?.firstOrNull()
				?.nextElementSibling()?.text() ?: manga.author,
			tags = info?.selectFirst("div.media-tags")
				?.select("a.media-tag-item")?.mapNotNullToSet { a ->
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
		val fullUrl = chapter.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		if (doc.location().endsWith("/register")) {
			throw AuthRequiredException(source)
		}
		val scripts = doc.head().select("script")
		val pg = (doc.body().getElementById("pg")?.html() ?: parseFailed("Element #pg not found"))
			.substringAfter('=')
			.substringBeforeLast(';')
		val pages = JSONArray(pg)
		for (script in scripts) {
			val raw = script.html().trim()
			if (raw.contains("window.__info")) {
				val json = JSONObject(
					raw.substringAfter("window.__info")
						.substringAfter('=')
						.substringBeforeLast(';'),
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
						referer = fullUrl,
						source = source,
					)
				}
			}
		}
		throw ParseException("Script with info not found")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val url = "https://${getDomain()}/manga-list"
		val doc = context.httpGet(url).parseHtml()
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
		throw ParseException("Script with genres not found")
	}

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(getDomain()).any {
				it.name.startsWith("remember_web_")
			}
		}

	override suspend fun getUsername(): String {
		val body = context.httpGet("https://${getDomain()}/messages").parseHtml().body()
		if (body.baseUri().endsWith("/login")) {
			throw AuthRequiredException(source)
		}
		return body.selectFirst(".profile-user__username")?.text() ?: parseFailed("Cannot find username")
	}

	protected open fun isNsfw(doc: Document): Boolean {
		val sidebar = doc.body().run {
			selectFirst(".media-sidebar") ?: selectFirst(".media-info")
		} ?: parseFailed("Sidebar not found")
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
		val domain = getDomain()
		val json = context.httpGet("https://$domain/search?type=manga&q=$query")
			.parseJsonArray()
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

	@MangaSourceParser("MANGALIB", "MangaLib", "ru")
	class Impl(context: MangaLoaderContext) : MangaLibParser(context, MangaSource.MANGALIB)
}