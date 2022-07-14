package org.koitharu.kotatsu.parsers.site

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*
import kotlin.math.pow

private const val DOMAIN_UNAUTHORIZED = "e-hentai.org"
private const val DOMAIN_AUTHORIZED = "exhentai.org"

@MangaSourceParser("EXHENTAI", "ExHentai")
internal class ExHentaiParser(
	override val context: MangaLoaderContext,
) : PagedMangaParser(MangaSource.EXHENTAI, pageSize = 25), MangaParserAuthProvider {

	override val sortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.NEWEST,
	)

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain(if (isAuthorized) DOMAIN_AUTHORIZED else DOMAIN_UNAUTHORIZED, null)

	override val authUrl: String
		get() = "https://${getDomain()}/bounce_login.php"

	private val ratingPattern = Regex("-?[0-9]+px")
	private val authCookies = arrayOf("ipb_member_id", "ipb_pass_hash")
	private var updateDm = false

	override val isAuthorized: Boolean
		get() {
			val authorized = isAuthorized(DOMAIN_UNAUTHORIZED)
			if (authorized) {
				if (!isAuthorized(DOMAIN_AUTHORIZED)) {
					context.cookieJar.copyCookies(
						DOMAIN_UNAUTHORIZED,
						DOMAIN_AUTHORIZED,
						authCookies,
					)
					context.cookieJar.insertCookies(DOMAIN_AUTHORIZED, "yay=louder")
				}
				return true
			}
			return false
		}

	init {
		context.cookieJar.insertCookies(DOMAIN_AUTHORIZED, "nw=1", "sl=dm_2")
		context.cookieJar.insertCookies(DOMAIN_UNAUTHORIZED, "nw=1", "sl=dm_2")
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		var search = query?.urlEncoded().orEmpty()
		val url = buildString {
			append("https://")
			append(getDomain())
			append("/?page=")
			append(page)
			if (!tags.isNullOrEmpty()) {
				var fCats = 0
				for (tag in tags) {
					tag.key.toIntOrNull()?.let { fCats = fCats or it } ?: run {
						search += tag.key + " "
					}
				}
				if (fCats != 0) {
					append("&f_cats=")
					append(1023 - fCats)
				}
			}
			if (search.isNotEmpty()) {
				append("&f_search=")
				append(search.trim().replace(' ', '+'))
			}
			// by unknown reason cookie "sl=dm_2" is ignored, so, we should request it again
			if (updateDm) {
				append("&inline_set=dm_e")
			}
		}
		val body = context.httpGet(url).parseHtml().body()
		val root = body.selectFirst("table.itg")
			?.selectFirst("tbody")
			?: if (updateDm) {
				parseFailed("Cannot find root")
			} else {
				updateDm = true
				return getListPage(page, query, tags, sortOrder)
			}
		updateDm = false
		return root.children().mapNotNull { tr ->
			if (tr.childrenSize() != 2) return@mapNotNull null
			val (td1, td2) = tr.children()
			val glink = td2.selectFirst("div.glink") ?: parseFailed("glink not found")
			val a = glink.parents().select("a").first() ?: parseFailed("link not found")
			val href = a.attrAsRelativeUrl("href")
			val tagsDiv = glink.nextElementSibling() ?: parseFailed("tags div not found")
			val mainTag = td2.selectFirst("div.cn")?.let { div ->
				MangaTag(
					title = div.text().toTitleCase(),
					key = tagIdByClass(div.classNames()) ?: return@let null,
					source = source,
				)
			}
			Manga(
				id = generateUid(href),
				title = glink.text().cleanupTitle(),
				altTitle = null,
				url = href,
				publicUrl = a.absUrl("href"),
				rating = td2.selectFirst("div.ir")?.parseRating() ?: RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = td1.selectFirst("img")?.absUrl("src").orEmpty(),
				tags = setOfNotNull(mainTag),
				state = null,
				author = tagsDiv.getElementsContainingOwnText("artist:").first()
					?.nextElementSibling()?.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml()
		val root = doc.body().selectFirst("div.gm") ?: parseFailed("Cannot find root")
		val cover = root.getElementById("gd1")?.children()?.first()
		val title = root.getElementById("gd2")
		val taglist = root.getElementById("taglist")
		val tabs = doc.body().selectFirst("table.ptt")?.selectFirst("tr")
		return manga.copy(
			title = title?.getElementById("gn")?.text()?.cleanupTitle() ?: manga.title,
			altTitle = title?.getElementById("gj")?.text()?.cleanupTitle() ?: manga.altTitle,
			publicUrl = doc.baseUri().ifEmpty { manga.publicUrl },
			rating = root.getElementById("rating_label")?.text()
				?.substringAfterLast(' ')
				?.toFloatOrNull()
				?.div(5f) ?: manga.rating,
			largeCoverUrl = cover?.styleValueOrNull("background")?.cssUrl(),
			description = taglist?.select("tr")?.joinToString("<br>") { tr ->
				val (tc, td) = tr.children()
				val subtags = td.select("a").joinToString { it.html() }
				"<b>${tc.html()}</b> $subtags"
			},
			chapters = tabs?.select("a")?.findLast { a ->
				a.text().toIntOrNull() != null
			}?.let { a ->
				val count = a.text().toInt()
				val chapters = ChaptersListBuilder(count)
				for (i in 1..count) {
					val url = "${manga.url}?p=${i - 1}"
					chapters += MangaChapter(
						id = generateUid(url),
						name = "${manga.title} #$i",
						number = i,
						url = url,
						uploadDate = 0L,
						source = source,
						scanlator = null,
						branch = null,
					)
				}
				chapters.toList()
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = context.httpGet(chapter.url.toAbsoluteUrl(getDomain())).parseHtml()
		val root = doc.body().getElementById("gdt") ?: parseFailed("Root not found")
		return root.select("a").map { a ->
			val url = a.attrAsRelativeUrl("href")
			MangaPage(
				id = generateUid(url),
				url = url,
				referer = a.absUrl("href"),
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = context.httpGet(page.url.toAbsoluteUrl(getDomain())).parseHtml()
		return doc.body().getElementById("img")?.absUrl("src")
			?: parseFailed("Image not found")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = context.httpGet("https://${getDomain()}").parseHtml()
		val root = doc.body().getElementById("searchbox")?.selectFirst("table")
			?: parseFailed("Root not found")
		return root.select("div.cs").mapNotNullToSet { div ->
			val id = div.id().substringAfterLast('_').toIntOrNull()
				?: return@mapNotNullToSet null
			MangaTag(
				title = div.text().toTitleCase(),
				key = id.toString(),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val doc = context.httpGet("https://forums.$DOMAIN_UNAUTHORIZED/").parseHtml().body()
		val username = doc.getElementById("userlinks")
			?.getElementsByAttributeValueContaining("href", "showuser=")
			?.firstOrNull()
			?.ownText()
			?: if (doc.getElementById("userlinksguest") != null) {
				throw AuthRequiredException(source)
			} else {
				throw ParseException(null)
			}
		return username
	}

	private fun isAuthorized(domain: String): Boolean {
		val cookies = context.cookieJar.getCookies(domain).mapToSet { x -> x.name }
		return authCookies.all { it in cookies }
	}

	private fun Element.parseRating(): Float {
		return runCatching {
			val style = requireNotNull(attr("style"))
			val (v1, v2) = ratingPattern.find(style)!!.destructured
			var p1 = v1.dropLast(2).toInt()
			val p2 = v2.dropLast(2).toInt()
			if (p2 != -1) {
				p1 += 8
			}
			(80 - p1) / 80f
		}.getOrDefault(RATING_UNKNOWN)
	}

	private fun String.cleanupTitle(): String {
		val result = StringBuilder(length)
		var skip = false
		for (c in this) {
			when {
				c == '[' -> skip = true
				c == ']' -> skip = false
				c.isWhitespace() && result.isEmpty() -> continue
				!skip -> result.append(c)
			}
		}
		while (result.lastOrNull()?.isWhitespace() == true) {
			result.deleteCharAt(result.lastIndex)
		}
		return result.toString()
	}

	private fun String.cssUrl(): String? {
		val fromIndex = indexOf("url(")
		if (fromIndex == -1) {
			return null
		}
		val toIndex = indexOf(')', startIndex = fromIndex)
		return if (toIndex == -1) {
			null
		} else {
			substring(fromIndex + 4, toIndex).trim()
		}
	}

	private fun tagIdByClass(classNames: Collection<String>): String? {
		val className = classNames.find { x -> x.startsWith("ct") } ?: return null
		val num = className.drop(2).toIntOrNull(16) ?: return null
		return 2.0.pow(num).toInt().toString()
	}
}