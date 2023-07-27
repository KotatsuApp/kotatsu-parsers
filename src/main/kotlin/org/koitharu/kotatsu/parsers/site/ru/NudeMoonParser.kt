package org.koitharu.kotatsu.parsers.site.ru

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

private const val MAX_THUMB_INDEX = 19

@MangaSourceParser("NUDEMOON", "Nude-Moon", "ru", type = ContentType.HENTAI)
internal class NudeMoonParser(
	context: MangaLoaderContext,
) : MangaParser(context, MangaSource.NUDEMOON), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain(
		"nude-moon.org",
		"nude-moon.net",
	)
	override val authUrl: String
		get() = "https://${domain}/index.php"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name == "fusion_user"
			}
		}

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	init {
		context.cookieJar.insertCookies(
			domain,
			"NMfYa=1;",
			"nm_mobile=0;",
		)
	}

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val domain = domain
		val url = when {
			!query.isNullOrEmpty() -> "https://$domain/search?stext=${query.urlEncoded()}&rowstart=$offset"
			!tags.isNullOrEmpty() -> tags.joinToString(
				separator = "_",
				prefix = "https://$domain/tags/",
				postfix = "&rowstart=$offset",
				transform = { it.key.urlEncoded() },
			)

			else -> "https://$domain/all_manga?${getSortKey(sortOrder)}&rowstart=$offset"
		}
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().run {
			selectFirst("td.main-bg") ?: selectFirst("td.main-body")
		} ?: doc.parseFailed("Cannot find root")
		return root.select("table.news_pic2").mapNotNull { row ->
			val a = row.selectFirst("td.bg_style1")?.selectFirst("a")
				?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			val title = a.selectFirst("h2")?.text().orEmpty()
			val info = row.selectFirst("td[width=100%]") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				url = href,
				title = title.substringAfter(" / "),
				altTitle = title.substringBefore(" / ", "")
					.takeUnless { it.isBlank() },
				author = info.getElementsContainingOwnText("Автор:").firstOrNull()
					?.nextElementSibling()?.ownText(),
				coverUrl = row.selectFirst("img.news_pic2")?.absUrl("data-src")
					.orEmpty(),
				tags = row.selectFirst("span.tag-links")?.select("a")
					?.mapToSet {
						MangaTag(
							title = it.text().toTitleCase(),
							key = it.attr("href").substringAfterLast('/'),
							source = source,
						)
					}.orEmpty(),
				source = source,
				publicUrl = a.absUrl("href"),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				description = row.selectFirst("div.description")?.html(),
				state = null,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val body = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().body()
		val root = body.selectFirst("table.shoutbox")
			?: body.parseFailed("Cannot find root")
		val info = root.select("div.tbl2")
		val lastInfo = info.last()
		return manga.copy(
			largeCoverUrl = body.selectFirst("img.news_pic2")?.absUrl("src"),
			description = info.select("div.blockquote").lastOrNull()?.html() ?: manga.description,
			tags = info.select("span.tag-links").firstOrNull()?.select("a")?.mapToSet {
				MangaTag(
					title = it.text().toTitleCase(),
					key = it.attr("href").substringAfterLast('/'),
					source = source,
				)
			}?.plus(manga.tags) ?: manga.tags,
			author = lastInfo?.getElementsByAttributeValueContaining("href", "mangaka/")?.text()
				?: manga.author,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					url = getReadLink(manga.url),
					source = source,
					number = 1,
					name = manga.title,
					scanlator = lastInfo?.getElementsByAttributeValueContaining("href", "perevod/")?.text(),
					uploadDate = lastInfo?.getElementsContainingOwnText("Дата:")
						?.firstOrNull()
						?.html()
						?.parseDate() ?: 0L,
					branch = null,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val mangaId = chapter.url.substringAfterLast('/').substringBefore('-').toIntOrNull()

		val script = doc.select("script").firstNotNullOfOrNull {
			it.html().takeIf { x -> x.contains(" images = new ") }
		} ?: if (isAuthorized) {
			doc.parseFailed("Cannot find pages list")
		} else {
			throw AuthRequiredException(source)
		}
		val pagesRegex = Regex("images\\[(\\d+)].src\\s*=\\s*'([^']+)'", RegexOption.MULTILINE)
		return pagesRegex.findAll(script).map { match ->
			val i = match.groupValues[1].toInt()
			val url = match.groupValues[2]
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = if (i <= MAX_THUMB_INDEX && mangaId != null) {
					val part2 = url.substringBeforeLast('/')
					val part3 = url.substringAfterLast('/')
					val part1 = part2.substringBeforeLast('/')
					"$part1/thumb/$mangaId/thumb_$part3"
				} else {
					null
				},
				source = source,
			)
		}.toList()
	}

	override suspend fun getTags(): Set<MangaTag> {
		val domain = domain
		val doc = webClient.httpGet("https://$domain/all_manga").parseHtml()
		val root = doc.body().getElementsContainingOwnText("Поиск манги по тегам")
			.firstOrNull()?.parents()?.find { it.tag().normalName() == "tbody" }
			?.selectFirst("td.textbox")?.selectFirst("td.small")
			?: doc.parseFailed("Tags root not found")
		return root.select("a").mapToSet {
			MangaTag(
				title = it.text().toTitleCase(),
				key = it.attr("href").substringAfterLast('/')
					.removeSuffix("+"),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/").parseHtml()
			.body()
		return body
			.getElementsContainingOwnText("Профиль")
			.firstOrNull()
			?.attr("href")
			?.substringAfterLast('/')
			?: run {
				throw if (body.selectFirst("form[name=\"loginform\"]") != null) {
					AuthRequiredException(source)
				} else {
					body.parseFailed("Cannot find username")
				}
			}
	}

	private fun getSortKey(sortOrder: SortOrder) =
		when (sortOrder) {
			SortOrder.POPULARITY -> "views"
			SortOrder.NEWEST -> "date"
			SortOrder.RATING -> "like"
			else -> "like"
		}

	private fun String.parseDate(): Long {
		val dateString = substringBetweenFirst("Дата:", "<")?.trim() ?: return 0
		val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
		return dateFormat.tryParse(dateString)
	}

	private fun getReadLink(url: String): String {
		val prefix = url.substringBefore('-', "")
		val suffix = url.substringAfter('-').trimStart('-')
		return "$prefix-online-$suffix"
	}
}
