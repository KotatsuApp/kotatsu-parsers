package org.koitharu.kotatsu.parsers.site.gattsu.pt

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.gattsu.GattsuParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("UNIVERSOHENTAI", "UniversoHentai", "pt", ContentType.HENTAI)
internal class UniversoHentai(context: MangaLoaderContext) :
	GattsuParser(context, MangaParserSource.UNIVERSOHENTAI, "universohentai.com") {

	override val tagPrefix = "category"

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/tags/").parseHtml()
		return doc.requireElementById("menu-topo").parseTags()
	}

	override fun Element.parseTags() = select("a").mapNotNullToSet {
		if (!it.attr("href").contains("/category/")) return@mapNotNullToSet null
		val key = it.attr("href").removeSuffix("/").substringAfterLast("/")
		MangaTag(
			key = key,
			title = it.text(),
			source = source,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val images = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("galeria")
			.select(".galeria-foto img")
		return images.map { img ->
			val urlImages = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(urlImages),
				url = urlImages,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(domain)
}
