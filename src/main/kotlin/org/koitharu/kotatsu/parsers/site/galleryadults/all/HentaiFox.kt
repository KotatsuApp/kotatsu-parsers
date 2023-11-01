package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("HENTAIFOX", "HentaiFox", type = ContentType.HENTAI)
internal class HentaiFox(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIFOX, "hentaifox.com") {
	override val selectGallery = ".lc_galleries .thumb, .related_galleries .thumb"
	override val selectTags = ".list_tags"
	override val selectTag = "ul.tags"
	override val urlReplaceBefore = "/gallery/"
	override val urlReplaceAfter = "/g/"
	override val selectLanguageChapter = "ul.languages a.tag_btn"

	override val selectTotalPage = ".total_pages"

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
				append("/tag/")
				append(tag?.key.orEmpty())
				if (page > 1) {
					append("/pag/")
					append(page)
					append("/")
				}
			} else if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				if (page > 1) {
					append("&page=")
					append(page)
				}
			} else {
				if (page > 2) {
					append("/pag/")
					append(page)
					append("/")
				} else if (page > 1) {
					append("/page/")
					append(page)
					append("/")
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.html().substringBefore("<")
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}
}
