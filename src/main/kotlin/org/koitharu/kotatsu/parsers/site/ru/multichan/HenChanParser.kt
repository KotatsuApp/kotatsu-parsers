package org.koitharu.kotatsu.parsers.site.ru.multichan

import okhttp3.HttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("HENCHAN", "Хентай-тян", "ru", type = ContentType.HENTAI)
internal class HenChanParser(context: MangaLoaderContext) : ChanParser(context, MangaParserSource.HENCHAN) {

	override val configKeyDomain = ConfigKey.Domain(
		"xxxx.henchan.pro",
		"xxl.hentaichan.live",
		"xxx.henchan.pro",
		"y.hentaichan.live",
		"xxx.hentaichan.live",
		"xx.hentaichan.live",
		"x.henchan.pro",
		"hentaichan.live",
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
					key = a.attr("href").substringAfterLast('/').urlDecode(),
					source = source,
				)
			} ?: manga.tags,
			chapters = listOf(
				MangaChapter(
					id = generateUid(readLink),
					url = readLink,
					source = source,
					number = 0f,
					volume = 0,
					uploadDate = 0L,
					name = manga.title,
					scanlator = null,
					branch = null,
				),
			),
		)
	}

	override fun buildUrl(offset: Int, order: SortOrder, filter: MangaListFilter): HttpUrl = when {
		filter.query.isNullOrEmpty() && filter.tags.isEmpty() && filter.tagsExclude.isEmpty() -> {
			val builder = urlBuilder().addQueryParameter("offset", offset.toString())
			when (order) {
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
			builder.build()
		}

		else -> {
			super.buildUrl(offset, order, filter)
		}
	}
}
