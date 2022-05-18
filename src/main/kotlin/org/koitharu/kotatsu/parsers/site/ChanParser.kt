package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class ChanParser(source: MangaSource) : MangaParser(source) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		val domain = getDomain()
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
		val doc = context.httpGet(url).parseHtml()
		val root = doc.body().selectFirst("div.main_fon")?.getElementById("content")
			?: parseFailed("Cannot find root")
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
				isNsfw = false,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.withDomain()).parseHtml()
		val root =
			doc.body().getElementById("dle-content") ?: parseFailed("Cannot find root")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
		return manga.copy(
			description = root.getElementById("description")?.html()?.substringBeforeLast("<div"),
			largeCoverUrl = root.getElementById("cover")?.absUrl("src"),
			chapters = root.select("table.table_cha tr:gt(1)").reversed().mapIndexedNotNull { i, tr ->
				val href = tr?.selectFirst("a")?.attrAsRelativeUrlOrNull("href")
					?: return@mapIndexedNotNull null
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
		val fullUrl = chapter.url.withDomain()
		val doc = context.httpGet(fullUrl).parseHtml()
		val scripts = doc.select("script")
		for (script in scripts) {
			val data = script.html()
			val pos = data.indexOf("\"fullimg")
			if (pos == -1) {
				continue
			}
			val json = data.substring(pos).substringAfter('[').substringBefore(';')
				.substringBeforeLast(']')
			val domain = getDomain()
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
					referer = fullUrl,
					source = source,
				)
			}
		}
		throw ParseException("Pages list not found at ${chapter.url}")
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = getDomain()
		val doc = context.httpGet("https://$domain/mostfavorites&sort=manga").parseHtml()
		val root = doc.body().selectFirst("div.main_fon")?.getElementById("side")
			?.select("ul")?.last() ?: throw ParseException("Cannot find root")
		return root.select("li.sidetag").mapToSet { li ->
			val a = li.children().last() ?: throw ParseException("a is null")
			MangaTag(
				title = a.text().toTagName(),
				key = a.attr("href").substringAfterLast('/'),
				source = source,
			)
		}
	}

	private fun getSortKey(sortOrder: SortOrder?) =
		when (sortOrder ?: sortOrders.minByOrNull { it.ordinal }) {
			SortOrder.ALPHABETICAL -> "catalog"
			SortOrder.POPULARITY -> "mostfavorites"
			SortOrder.NEWEST -> "manga/new"
			else -> "mostfavorites"
		}

	private fun getSortKey2(sortOrder: SortOrder?) =
		when (sortOrder ?: sortOrders.minByOrNull { it.ordinal }) {
			SortOrder.ALPHABETICAL -> "abcasc"
			SortOrder.POPULARITY -> "favdesc"
			SortOrder.NEWEST -> "datedesc"
			else -> "favdesc"
		}

	private fun String.toTagName() = replace('_', ' ').toTitleCase()
}