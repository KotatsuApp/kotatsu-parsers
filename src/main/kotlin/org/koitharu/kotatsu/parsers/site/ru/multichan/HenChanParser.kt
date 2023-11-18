package org.koitharu.kotatsu.parsers.site.ru.multichan

import okhttp3.HttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENCHAN", "Хентай-тян", "ru", type = ContentType.HENTAI)
internal class HenChanParser(context: MangaLoaderContext) : ChanParser(context, MangaSource.HENCHAN) {

	override val configKeyDomain = ConfigKey.Domain(
		"x.henchan.pro",
		"xxx.henchan.pro",
		"y.hentaichan.live",
		"xxx.hentaichan.live",
		"xx.hentaichan.live",
		"hentaichan.live",
		"hentaichan.pro",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.RATING,
	)

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("dle-content")
		val readLink = manga.url.replace("manga", "online")
		return manga.copy(
			description = root.getElementById("description")?.html()?.substringBeforeLast("<div"),
			largeCoverUrl = root.getElementById("cover")?.absUrl("src"),
			tags = root.selectFirst("div.sidetags")?.select("li.sidetag")?.mapToSet {
				val a = it.children().last() ?: doc.parseFailed("Invalid tag")
				MangaTag(
					title = a.text().toTitleCase(),
					key = a.attr("href").substringAfterLast('/'),
					source = source,
				)
			} ?: manga.tags,
			chapters = listOf(
				MangaChapter(
					id = generateUid(readLink),
					url = readLink,
					source = source,
					number = 1,
					uploadDate = 0L,
					name = manga.title,
					scanlator = null,
					branch = null,
				),
			),
		)
	}

	override fun buildUrl(offset: Int, query: String?, tags: Set<MangaTag>?, sortOrder: SortOrder): HttpUrl {
		if (query.isNullOrEmpty() && tags.isNullOrEmpty()) {
			val builder = urlBuilder().addQueryParameter("offset", offset.toString())
			when (sortOrder) {
				SortOrder.POPULARITY -> {
					builder.addPathSegment("mostviews")
					builder.addQueryParameter("sort", "manga")
				}

				SortOrder.RATING -> {
					builder.addPathSegment("mostfavorites")
					builder.addQueryParameter("sort", "manga")
				}

				else -> { // SortOrder.NEWEST
					builder.addPathSegment("manga")
					builder.addPathSegment("newest")
				}
			}
			return builder.build()
		}
		return super.buildUrl(offset, query, tags, sortOrder)
	}
}
