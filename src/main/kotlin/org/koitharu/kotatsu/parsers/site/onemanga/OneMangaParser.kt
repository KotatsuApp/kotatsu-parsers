package org.koitharu.kotatsu.parsers.site.onemanga

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class OneMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 1,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isMultipleTagsSupported = false

	override val isSearchSupported = false

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if (page > 1) {
			return emptyList()
		}
		val url = "https://$domain"
		val doc = webClient.httpGet(url).parseHtml()
		val manga = ArrayList<Manga>()
		manga.add(
			Manga(
				id = generateUid(url),
				url = url,
				publicUrl = url,
				coverUrl = doc.selectFirst("div.elementor-widget-container img")?.src().orEmpty(),
				title = doc.selectFirst("ul.elementor-nav-menu li a")?.text().orEmpty(),
				altTitle = doc.selectFirst("div.elementor-widget-text-editor ul li:contains(Nom(s) Alternatif(s))")
					?.text()?.replace("Nom(s) Alternatif(s) :", "").orEmpty(),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = doc.selectFirst("div.elementor-widget-text-editor ul li:contains(Auteur(s))")?.text()
					?.replace("Auteur(s): ", "").orEmpty(),
				description = doc.selectLast("div.elementor-widget-text-editor ul li")?.text().orEmpty(),
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			),
		)
		return manga
	}

	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return manga.copy(
			chapters = doc.requireElementById("All_chapters").select("ul li a")
				.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = 0L,
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select("div.elementor-widget-container img").map { img ->
			val url = img.src() ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
