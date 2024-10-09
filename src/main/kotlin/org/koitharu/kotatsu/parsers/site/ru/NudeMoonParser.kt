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

@MangaSourceParser("NUDEMOON", "Nude-Moon", "ru", type = ContentType.HENTAI)
internal class NudeMoonParser(
	context: MangaLoaderContext,
) : MangaParser(context, MangaParserSource.NUDEMOON), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain(
		"b.nude-moon.fun",
		"x.nude-moon.fun",
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

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	init {
		context.cookieJar.insertCookies(
			domain,
			"NMfYa=1;",
			"nm_mobile=1;",
		)
	}

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val domain = domain

		val url = when {
			!filter.query.isNullOrEmpty() -> {
				if (!isAuthorized) {
					throw AuthRequiredException(source)
				}
				"https://$domain/search?stext=${filter.query.urlEncoded()}&rowstart=$offset"
			}

			else -> {
				if (filter.tags.isNotEmpty()) {
					filter.tags.joinToString(
						separator = "_",
						prefix = "https://$domain/tags/",
						postfix = "&rowstart=$offset",
						transform = { it.key.urlEncoded() },
					)
				} else {
					val order = when (order) {
						SortOrder.POPULARITY -> "views"
						SortOrder.NEWEST -> "date"
						SortOrder.RATING -> "like"
						else -> "like"
					}
					"https://$domain/all_manga?$order&rowstart=$offset"
				}
			}

		}

		val doc = webClient.httpGet(url).parseHtml()
		return doc.body().select("table.news_pic2").mapNotNull { row ->
			val a = row.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			val title = a.attr("title")
			Manga(
				id = generateUid(href),
				url = href,
				title = title.substringAfter(" / "),
				altTitle = title.substringBefore(" / ", "").takeUnless { it.isBlank() },
				author = row.getElementsByAttributeValueContaining("href", "/mangaka/").firstOrNull()?.textOrNull(),
				coverUrl = row.selectFirst("img")?.absUrl("src").orEmpty(),
				tags = row.selectFirst(".tag-links")?.select("a")?.mapToSet {
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
		val root = body.selectFirstOrThrow("table.news_pic2")
		val dateFormat = SimpleDateFormat("dd MMMM yyyy", sourceLocale)
		return manga.copy(
			largeCoverUrl = body.selectFirstOrThrow("img[data-src]").attrAsAbsoluteUrl("data-src"),
			description = root.selectFirst(".description")?.html() ?: manga.description,
			tags = root.getElementsByAttributeValueContaining("href", "/tag/").mapToSet {
				MangaTag(
					title = it.text().toTitleCase(),
					key = it.attr("href").substringAfterLast('/'),
					source = source,
				)
			} + manga.tags,
			author = root.getElementsByAttributeValueContaining("href", "/mangaka/").firstOrNull()?.text()
				?: manga.author,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					url = manga.url,
					source = source,
					number = 0f,
					volume = 0,
					name = manga.title,
					scanlator = root.getElementsByAttributeValueContaining("href", "/perevod/").firstOrNull()
						?.textOrNull(),
					uploadDate = dateFormat.tryParse(
						root.getElementsByAttributeValueEnding("src", "ico/time.png").firstOrNull()
							?.nextElementSibling()?.text(),
					),
					branch = null,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val pages = doc.select("img[data-src]")
		return pages.map { img ->
			val url = img.attrAsRelativeUrl("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}.toList()
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val domain = domain
		val doc = webClient.httpGet("https://$domain/tags").parseHtml()
		val root = doc.body().getElementsByAttributeValue("name", "multitags").first()
			?: doc.parseFailed("Tags form not found")
		return root.select("input").mapToSet {
			val value = it.attr("value").trim()
			MangaTag(
				title = value.toTitleCase(sourceLocale),
				key = value.replace(' ', '_'),
				source = source,
			)
		}
	}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/").parseHtml().body()
		return body.getElementsContainingOwnText("Профиль").firstOrNull()?.attr("href")?.substringAfterLast('/')
			?: run {
				throw if (body.selectFirst("form[name=\"loginform\"]") != null) {
					AuthRequiredException(source)
				} else {
					body.parseFailed("Cannot find username")
				}
			}
	}
}
