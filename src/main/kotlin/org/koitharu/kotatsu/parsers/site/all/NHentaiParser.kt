package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NHENTAI", "N-Hentai", type = ContentType.HENTAI)
class NHentaiParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.NHENTAI, pageSize = 25) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("nhentai.net")

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.NEWEST, SortOrder.POPULARITY)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query.isNullOrEmpty() && tags != null && tags.size > 1) {
			return getListPage(page, buildQuery(tags), emptySet(), sortOrder)
		}
		val domain = domain
		val url = buildString {
			append("https://")
			append(domain)
			if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				append("&page=")
				append(page)
				if (sortOrder == SortOrder.POPULARITY) {
					append("&sort=popular")
				}
			} else {
				append('/')
				if (!tags.isNullOrEmpty()) {
					val tag = tags.single()
					append("tag/")
					append(tag.key)
					append('/')
					if (sortOrder == SortOrder.POPULARITY) {
						append("popular")
					}
					append("?page=")
					append(page)
				} else {
					if (sortOrder == SortOrder.POPULARITY) {
						append("?sort=popular&page=")
					} else {
						append("?page=")
					}
					append(page)
				}
			}
		}
		val root = webClient.httpGet(url).parseHtml().body().requireElementById("content")
			.selectLastOrThrow("div.index-container")
		val regexBrackets = Regex("\\[[^]]+]|\\([^)]+\\)")
		val regexSpaces = Regex("\\s+")
		return root.select(".gallery").map { div ->
			val a = div.selectFirstOrThrow("a.cover")
			val href = a.attrAsRelativeUrl("href")
			val img = div.selectFirstOrThrow("img")
			val title = div.selectFirstOrThrow(".caption").text()
			Manga(
				id = generateUid(href),
				title = title.replace(regexBrackets, "")
					.replace(regexSpaces, " ")
					.trim(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = img.attrAsAbsoluteUrlOrNull("data-src")
					?: img.attrAsAbsoluteUrl("src"),
				tags = setOf(),
				state = null,
				author = null,
				largeCoverUrl = null,
				description = null,
				chapters = listOf(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(
			url = manga.url.toAbsoluteUrl(domain),
		).parseHtml().body().requireElementById("bigcontainer")
		val img = root.requireElementById("cover").selectFirstOrThrow("img")
		val tagContainers = root.requireElementById("tags").select(".tag-container")
		val dateFormat = SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'",
			Locale.ROOT,
		)
		return manga.copy(
			tags = tagContainers.find { x -> x.ownText() == "Tags:" }?.parseTags() ?: manga.tags,
			author = tagContainers.find { x -> x.ownText() == "Artists:" }
				?.selectFirst("span.name")?.text()?.toCamelCase(),
			largeCoverUrl = img.attrAsAbsoluteUrlOrNull("data-src")
				?: img.attrAsAbsoluteUrl("src"),
			description = null,
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(
						tagContainers.find { x -> x.ownText() == "Uploaded:" }
							?.selectFirst("time")
							?.attr("datetime"),
					),
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = chapter.url.toAbsoluteUrl(domain)
		val root = webClient.httpGet(url).parseHtml().requireElementById("thumbnail-container")
		return root.select(".thumb-container").map { div ->
			val a = div.selectFirstOrThrow("a")
			val img = div.selectFirstOrThrow("img")
			val href = a.attrAsRelativeUrl("href")
			MangaPage(
				id = generateUid(href),
				url = href,
				preview = img.attrAsAbsoluteUrlOrNull("data-src")
					?: img.attrAsAbsoluteUrl("src"),
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val root = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml().body()
			.requireElementById("image-container")
		return root.selectFirstOrThrow("img").attrAsAbsoluteUrl("src")
	}

	override suspend fun getTags(): Set<MangaTag> {
		return coroutineScope {
			// parse first 3 pages of tags
			(1..3).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val root = webClient.httpGet("https://${domain}/tags/popular?page=$page").parseHtml().body()
			.getElementById("tag-container")
		return root?.parseTags().orEmpty()
	}

	private fun Element.parseTags() = select("a.tag").mapToSet { a ->
		val href = a.attr("href").removeSuffix('/')
		MangaTag(
			title = a.selectFirstOrThrow(".name").text().toTitleCase(),
			key = href.substringAfterLast('/'),
			source = source,
		)
	}

	private fun buildQuery(tags: Collection<MangaTag>) = tags.joinToString(separator = " ") { tag ->
		"tag:\"${tag.key}\""
	}
}
