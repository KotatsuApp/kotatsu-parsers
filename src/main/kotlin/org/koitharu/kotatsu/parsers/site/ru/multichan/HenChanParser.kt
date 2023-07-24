package org.koitharu.kotatsu.parsers.site.ru.multichan

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase

@MangaSourceParser("HENCHAN", "Хентай-тян", "ru")
internal class HenChanParser(context: MangaLoaderContext) : ChanParser(context, MangaSource.HENCHAN) {

	override val configKeyDomain = ConfigKey.Domain(
		"y.hentaichan.live",
		"xxx.hentaichan.live",
		"xx.hentaichan.live",
		"hentaichan.live",
		"hentaichan.pro",
	)

	override val isNsfwSource = true

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
}
