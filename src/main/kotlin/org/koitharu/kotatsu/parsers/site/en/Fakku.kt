package org.koitharu.kotatsu.parsers.site.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.util.*

@MangaSourceParser("FAKKU", "Fakku", "en", ContentType.HENTAI)
internal class Fakku(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.FAKKU, pageSize = 25) {

	override val sortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.ALPHABETICAL, SortOrder.NEWEST, SortOrder.UPDATED)

	override val configKeyDomain = ConfigKey.Domain("fakku.cc")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!query.isNullOrEmpty() -> {
					append("/search?q=")
					append(query.urlEncoded())
					append("&")
				}

				!tags.isNullOrEmpty() -> {
					append("/tags/")
					append(tag?.key.orEmpty())
					append("?")
				}

				else -> {
					append("?")
				}
			}
			append("page=")
			append(page)
			append("&sort=")
			when (sortOrder) {
				SortOrder.ALPHABETICAL -> append("title")
				SortOrder.NEWEST -> append("created_at")
				SortOrder.UPDATED -> append("published_at")
				else -> append("published_at")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.entries .entry a").map { a ->
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = a.selectFirst("img")?.src().orEmpty(),
				title = a.selectFirst(".title")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val root = webClient.httpGet("https://$domain/tags").parseHtml()
		return root.select("div.entries .entry a").mapToSet {
			MangaTag(
				key = it.attr("href").substringAfterLast("/"),
				title = it.selectFirstOrThrow(".name").text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val genreDeferred = async {
			webClient.httpGet(manga.url.toAbsoluteUrl(domain) + ".json").parseJson()
		}
		val genre = genreDeferred.await()
		manga.copy(
			author = doc.selectFirst("tr.artists a")?.text(),
			tags = if (genre.toString().contains("tags")) {
				genre.getJSONArray("tags").mapJSONToSet {
					MangaTag(
						key = it.getString("slug"),
						title = it.getString("name"),
						source = source,
					)
				}
			} else {
				emptySet()
			},
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1,
					url = manga.url + "/1",
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val totalPages = doc.selectFirstOrThrow(".total").text().toInt()
		val rawUrl = chapter.url.substringBeforeLast("/")
		return (1..totalPages).map {
			val url = "$rawUrl/$it"
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
		val root = doc.body()
		return root.selectFirstOrThrow(".page img").attrAsAbsoluteUrl("src")
	}
}
