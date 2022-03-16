package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toTitleCase

internal class HenChanParser(override val context: MangaLoaderContext) : ChanParser(MangaSource.HENCHAN) {

	override val configKeyDomain = ConfigKey.Domain("hentaichan.live", null)

	override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		return super.getList(offset, query, tags, sortOrder).map {
			it.copy(
				coverUrl = it.coverUrl.replace("_blur", ""),
				isNsfw = true,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.url.withDomain()).parseHtml()
		val root =
			doc.body().getElementById("dle-content") ?: throw ParseException("Cannot find root")
		val readLink = manga.url.replace("manga", "online")
		return manga.copy(
			description = root.getElementById("description")?.html()?.substringBeforeLast("<div"),
			largeCoverUrl = root.getElementById("cover")?.absUrl("src"),
			tags = root.selectFirst("div.sidetags")?.select("li.sidetag")?.mapToSet {
				val a = it.children().last() ?: parseFailed("Invalid tag")
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
}