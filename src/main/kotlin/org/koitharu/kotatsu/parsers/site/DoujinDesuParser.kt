package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("DOUJINDESU", "DoujinDesu", "id")
class DoujinDesuParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.DOUJINDESU, pageSize = 18) {
	@InternalParsersApi
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("212.32.226.234", null)

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.NEWEST, SortOrder.ALPHABETICAL, SortOrder.POPULARITY)

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().selectFirstOrThrow("#archive")
		val metadataEl = docs.selectFirst(".wrapper > .metadata tbody")
		val state = when (metadataEl?.selectFirst("tr:contains(Status)")?.selectLast("td")?.text()) {
			"Finished" -> MangaState.FINISHED
			"Publishing" -> MangaState.ONGOING
			else -> null
		}
		return manga.copy(
			author = metadataEl?.selectFirst("tr:contains(Author)")?.selectLast("td")?.text(),
			description = docs.selectFirst(".wrapper > .metadata > .pb-2")?.selectFirst("p")?.html(),
			state = state,
			rating = metadataEl?.selectFirst(".rating-prc")?.ownText()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
			tags = docs.select(".tags > a").mapToSet {
				MangaTag(
					key = it.attr("title"),
					title = it.text(),
					source = source,
				)
			},
			chapters = docs.select("#chapter_list > ul > li").mapChapters(reversed = true) { index, element ->
				val titleTag = element.selectFirstOrThrow(".epsleft > .lchx > a")
				val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("in"))
				MangaChapter(
					id = generateUid(titleTag.attrAsRelativeUrl("href")),
					name = titleTag.text(),
					number = index + 1,
					url = titleTag.attrAsRelativeUrl("href"),
					scanlator = null,
					uploadDate = dateFormat.tryParse(element.select(".epsleft > .date").text()),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder
	): List<Manga> {
		val url = urlBuilder().apply {
			addPathSegment("manga")
			addPathSegment("page")
			addPathSegment("$page/")
			val order = when (sortOrder) {
				SortOrder.UPDATED -> "update"
				SortOrder.POPULARITY -> "popular"
				SortOrder.ALPHABETICAL -> "title"
				SortOrder.NEWEST -> "latest"
				else -> throw IllegalArgumentException("Sort order not supported")
			}
			addQueryParameter("order", order)
			addQueryParameter("title", query.orEmpty())
			tags?.forEach {
				addEncodedQueryParameter("genre[]".urlEncoded(), it.key.urlEncoded())
			}
		}.build()

		return webClient.httpGet(url).parseHtml().select("#archives > div.entries > .entry")
			.map {
				val titleTag = it.selectFirstOrThrow(".metadata > a")
				val relativeUrl = titleTag.attrAsRelativeUrl("href")
				Manga(
					id = generateUid(relativeUrl),
					title = titleTag.attr("title"),
					altTitle = null,
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = true,
					coverUrl = it.selectFirst(".thumbnail > img")?.attrAsAbsoluteUrl("src").orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					largeCoverUrl = null,
					description = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val id = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
			.selectFirstOrThrow("#reader")
			.attr("data-id")
		return webClient.httpPost("/themes/ajax/ch.php".toAbsoluteUrl(domain), "id=$id").parseHtml()
			.select("img")
			.map {
				val url = it.attrAsRelativeUrl("src")
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return webClient.httpGet("/genre/".toAbsoluteUrl(domain)).parseHtml()
			.select("#taxonomy .entries > .entry > a")
			.mapToSet {
				MangaTag(
					key = it.attr("title"),
					title = it.attr("title"),
					source = source,
				)
			}
	}
}
