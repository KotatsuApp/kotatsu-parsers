package org.koitharu.kotatsu.parsers.site.multichan

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.getCookies
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.removeSurrounding
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

internal abstract class ChanParser(
	context: MangaLoaderContext,
	source: MangaSource,
) : MangaParser(context, source), MangaParserAuthProvider {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	protected open val isNsfwSource = false

	override val authUrl: String
		get() = "https://${domain}"

	override val isAuthorized: Boolean
		get() = context.cookieJar.getCookies(domain).any { it.name == "dle_user_id" }

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val domain = domain
		val url = when {
			!query.isNullOrEmpty() -> {
				if (offset != 0) {
					return emptyList()
				}
				"https://$domain/?do=search&subaction=search&story=${query.urlEncoded()}"
			}

			!tags.isNullOrEmpty() -> tags.joinToString(
				prefix = "https://$domain/tags/",
				postfix = "&n=${getSortKey2(sortOrder)}?offset=$offset",
				separator = "+",
			) { tag -> tag.key }

			else -> "https://$domain/${getSortKey(sortOrder)}?offset=$offset"
		}
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirst("div.main_fon")?.getElementById("content")
			?: doc.parseFailed("Cannot find root")
		return root.select("div.content_row").mapNotNull { row ->
			val a = row.selectFirst("div.manga_row1")?.selectFirst("h2")?.selectFirst("a")
				?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				altTitle = a.attr("title"),
				title = a.text().substringAfterLast('(').substringBeforeLast(')'),
				author = row.getElementsByAttributeValueStarting(
					"href",
					"/mangaka",
				).firstOrNull()?.text(),
				coverUrl = row.selectFirst("div.manga_images")?.selectFirst("img")
					?.absUrl("src").orEmpty(),
				tags = runCatching {
					row.selectFirst("div.genre")?.select("a")?.mapToSet {
						MangaTag(
							title = it.text().toTagName(),
							key = it.attr("href").substringAfterLast('/').urlEncoded(),
							source = source,
						)
					}
				}.getOrNull().orEmpty(),
				rating = RATING_UNKNOWN,
				state = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("dle-content")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
		return manga.copy(
			description = root.getElementById("description")?.html()?.substringBeforeLast("<div"),
			largeCoverUrl = root.getElementById("cover")?.absUrl("src"),
			chapters = root.select("table.table_cha tr:gt(1)").mapChapters(reversed = true) { i, tr ->
				val href = tr?.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
					?: return@mapChapters null
				MangaChapter(
					id = generateUid(href),
					name = tr.selectFirst("a")?.text().orEmpty(),
					number = i + 1,
					url = href,
					scanlator = null,
					branch = null,
					uploadDate = dateFormat.tryParse(tr.selectFirst("div.date")?.text()),
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			val pos = data.indexOf("\"fullimg")
			if (pos == -1) {
				continue
			}
			val json = data.substring(pos).substringAfter('[').substringBefore(';')
				.substringBeforeLast(']')
			val domain = domain
			return json.split(",").mapNotNull {
				it.trim()
					.removeSurrounding('"', '\'')
					.toRelativeUrl(domain)
					.takeUnless(String::isBlank)
			}.map { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
		doc.parseFailed("Pages list not found at ${chapter.url}")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = domain
		val doc = webClient.httpGet("https://$domain/mostfavorites&sort=manga").parseHtml()
		val root = doc.body().selectFirst("div.main_fon")?.getElementById("side")
			?.select("ul")?.last() ?: doc.parseFailed("Cannot find root")
		return root.select("li.sidetag").mapToSet { li ->
			val a = li.children().lastOrNull() ?: li.parseFailed("a is null")
			MangaTag(
				title = a.text().toTagName(),
				key = a.attr("href").substringAfterLast('/'),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val doc = webClient.httpGet("https://${domain}").parseHtml().body()
		val root = doc.requireElementById("top_user")
		val a = root.getElementsByAttributeValueContaining("href", "/user/").firstOrNull()
			?: throw AuthRequiredException(source)
		return a.attr("href").removeSuffix('/').substringAfterLast('/')
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.replace("/manga/", "/related/").toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("right")
		return root.select("div.related").mapNotNull { div ->
			val info = div.selectFirst(".related_info") ?: return@mapNotNull null
			val a = info.selectFirst("a") ?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				altTitle = a.attr("title"),
				title = a.text().substringAfterLast('(').substringBeforeLast(')'),
				author = info.getElementsByAttributeValueStarting(
					"href",
					"/mangaka",
				).firstOrNull()?.text(),
				coverUrl = div.selectFirst("img")?.absUrl("src").orEmpty(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.ALPHABETICAL -> "catalog"
			SortOrder.POPULARITY -> "mostfavorites"
			SortOrder.NEWEST -> "manga/new"
			else -> "mostfavorites"
		}

	private fun getSortKey2(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.ALPHABETICAL -> "abcasc"
			SortOrder.POPULARITY -> "favdesc"
			SortOrder.NEWEST -> "datedesc"
			else -> "favdesc"
		}

	private fun String.toTagName() = replace('_', ' ').toTitleCase()
}
