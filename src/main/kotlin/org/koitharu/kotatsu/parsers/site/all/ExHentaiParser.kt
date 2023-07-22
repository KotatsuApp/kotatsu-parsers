package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.SparseArrayCompat
import androidx.collection.set
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.ChaptersListBuilder
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.copyCookies
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.insertCookies
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.styleValueOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.Collections
import kotlin.math.pow

private const val DOMAIN_UNAUTHORIZED = "e-hentai.org"
private const val DOMAIN_AUTHORIZED = "exhentai.org"

@MangaSourceParser("EXHENTAI", "ExHentai")
internal class ExHentaiParser(
	context: MangaLoaderContext,
) : PagedMangaParser(context, MangaSource.EXHENTAI, pageSize = 25), MangaParserAuthProvider {

	override val sortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.NEWEST,
	)

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain(
			if (isAuthorized) DOMAIN_AUTHORIZED else DOMAIN_UNAUTHORIZED,
			if (isAuthorized) DOMAIN_UNAUTHORIZED else DOMAIN_AUTHORIZED,
		)

	override val authUrl: String
		get() = "https://${domain}/bounce_login.php"

	private val ratingPattern = Regex("-?[0-9]+px")
	private val authCookies = arrayOf("ipb_member_id", "ipb_pass_hash")
	private var updateDm = false
	private val nextPages = SparseArrayCompat<Long>()
	private val suspiciousContentKey = ConfigKey.ShowSuspiciousContent(true)

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
		paginator.firstPage = 0
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		var search = query?.urlEncoded().orEmpty()
		val next = nextPages.get(page, 0L)
		if (page > 0 && next == 0L) {
			assert(false) { "Page timestamp not found" }
			return emptyList()
		}
		val url = buildString {
			append("https://")
			append(domain)
			append("/?next=")
			append(next)
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
			append("&advsearch=1")
			if (config[suspiciousContentKey]) {
				append("&f_sh=on")
			}
		}
		val body = webClient.httpGet(url).parseHtml().body()
		val root = body.selectFirst("table.itg")
			?.selectFirst("tbody")
			?: if (updateDm) {
				body.parseFailed("Cannot find root")
			} else {
				updateDm = true
				return getListPage(page, query, tags, sortOrder)
			}
		updateDm = false
		nextPages[page + 1] = getNextTimestamp(body)
		return root.children().mapNotNull { tr ->
			if (tr.childrenSize() != 2) return@mapNotNull null
			val (td1, td2) = tr.children()
			val glink = td2.selectFirstOrThrow("div.glink")
			val a = glink.parents().select("a").first() ?: glink.parseFailed("link not found")
			val href = a.attrAsRelativeUrl("href")
			val tagsDiv = glink.nextElementSibling() ?: glink.parseFailed("tags div not found")
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
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow("div.gm")
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
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("gdt")
		return root.select("a").map { a ->
			val url = a.attrAsRelativeUrl("href")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.body().requireElementById("img").attrAsAbsoluteUrl("src")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}").parseHtml()
		val root = doc.body().requireElementById("searchbox").selectFirstOrThrow("table")
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
		val doc = webClient.httpGet("https://forums.$DOMAIN_UNAUTHORIZED/").parseHtml().body()
		val username = doc.getElementById("userlinks")
			?.getElementsByAttributeValueContaining("href", "showuser=")
			?.firstOrNull()
			?.ownText()
			?: if (doc.getElementById("userlinksguest") != null) {
				throw AuthRequiredException(source)
			} else {
				doc.parseFailed()
			}
		return username
	}

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(suspiciousContentKey)
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

	private fun getNextTimestamp(root: Element): Long {
		return root.getElementById("unext")
			?.attrAsAbsoluteUrlOrNull("href")
			?.toHttpUrlOrNull()
			?.queryParameter("next")
			?.toLongOrNull() ?: 1
	}
}
