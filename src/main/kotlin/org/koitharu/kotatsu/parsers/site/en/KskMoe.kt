package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("KSKMOE", "Ksk .Moe", "en", ContentType.HENTAI)
internal class KskMoe(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.KSKMOE, 35) {

	override val sortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST, SortOrder.ALPHABETICAL)
	override val configKeyDomain = ConfigKey.Domain("ksk.moe")

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

			if (!tags.isNullOrEmpty()) {
				append("/tags/")
				append(tag?.key.orEmpty())
			} else {
				append("/browse")
			}

			if (page > 1) {
				append("/page/")
				append(page)
			}

			when (sortOrder) {
				SortOrder.POPULARITY -> append("?sort=32")
				SortOrder.UPDATED -> append("")
				SortOrder.NEWEST -> append("?sort=16")
				SortOrder.ALPHABETICAL -> append("?sort=1")
				else -> append("")
			}

			if (!query.isNullOrEmpty()) {
				append("?s=")
				append(query.urlEncoded())
			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		if (!doc.html().contains("pagination") && page > 1) {
			return emptyList()
		}
		return doc.requireElementById("galleries").select("article").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = div.selectLastOrThrow("h3 span").text(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = true,
				coverUrl = div.selectFirstOrThrow("img").src()?.toAbsoluteUrl(domain).orEmpty(),
				tags = div.select("footer span").mapNotNullToSet { span ->
					MangaTag(
						key = span.text().urlEncoded(),
						title = span.text(),
						source = source,
					)
				},
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return coroutineScope {
			(1..2).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val url = if (page == 1) {
			"https://$domain/tags"
		} else {
			"https://$domain/tags/page/$page"
		}
		val root = webClient.httpGet(url).parseHtml().body().getElementById("tags")
		return root?.parseTags().orEmpty()
	}

	private fun Element.parseTags() = select("section.tags div a").mapToSet { a ->
		MangaTag(
			key = a.attr("href").substringAfterLast("/tags/"),
			title = a.selectFirstOrThrow("span").text(),
			source = source,
		)
	}

	private val date = SimpleDateFormat("dd.MM.yyyy hh:mm 'UTC'", Locale.US)
	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()

		return manga.copy(
			tags = doc.requireElementById("metadata").select("main div:contains(Tag) a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("/tags/"),
					title = a.selectFirstOrThrow("span").text(),
					source = source,
				)
			},
			author = doc.requireElementById("metadata").selectFirstOrThrow("main div:contains(Artist) a span").text(),
			chapters = listOf(
				MangaChapter(
					id = generateUid(manga.id),
					name = manga.title,
					number = 1,
					url = manga.url,
					scanlator = null,
					uploadDate = date.tryParse(doc.selectFirstOrThrow("time.updated").text()),
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url
			.replace("/view/", "/read/")
			.let { "$it/1" }
			.toAbsoluteUrl(domain)
		val document = webClient.httpGet(fullUrl).parseHtml()

		val id = fullUrl
			.substringAfter("/read/")
			.substringBeforeLast("/")

		val cdnUrl = document.selectFirst("meta[itemprop=image]")
			?.attr("content")
			?.toHttpUrlOrNull()
			?.host
			.let { "https://" + (it ?: domain) }

		val script = document.select("script:containsData(window.metadata)").html()

		val rawJson = script
			.substringAfter("original:")
			.substringBefore("resampled:")
			.substringBeforeLast(",")

		return JSONArray(rawJson).mapJSON {
			val fileName = it.getString("n")

			val url = "$cdnUrl/original/$id/$fileName"
			val preview = "$cdnUrl/t/$id/320/$fileName"

			MangaPage(
				id = generateUid(url),
				url = url,
				preview = preview,
				source = source,
			)
		}
	}
}
