package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("NHENTAI", "NHentai", type = ContentType.HENTAI)
internal class NHentaiParser(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.NHENTAI, "nhentai.net", 25) {
	override val selectGallery = "div.index-container:not(.index-popular) .gallery, #related-container .gallery"
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".caption"
	override val pathTagUrl = "/tags/popular?page="
	override val selectTags = "#tag-container a"
	override val selectTag = ".tag-container:contains(Tags:) span.tags"
	override val selectAuthor = "#tags div.tag-container:contains(Artists:) span.name"
	override val selectLanguageChapter =
		".tag-container:contains(Languages:) span.tags a:not(.tag-17249) span.name" // tag-17249 = translated
	override val idImg = "image-container"
	override val listLanguage = arrayOf(
		"/english",
		"/japanese",
		"/chinese",
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query.isNullOrEmpty() && tags != null && tags.size > 1) {
			return getListPage(page, buildQuery(tags), emptySet(), sortOrder)
		}
		val url = buildString {
			append("https://")
			append(domain)
			if (!tags.isNullOrEmpty()) {
				val tag = tags.single()
				if (tag.key == "languageKey") {
					append("/language")
					append(tag.title)
					append("/?")
				} else {
					append("/tag/")
					append(tag.key)
					append("/?")
				}
			} else if (!query.isNullOrEmpty()) {
				append("/search/?q=")
				append(query.urlEncoded())
				append("&")
			} else {
				append("/?")
			}
			append("page=")
			append(page)
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.requireElementById(idImg).selectFirstOrThrow("img").src() ?: root.parseFailed("Image src not found")
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

	private fun buildQuery(tags: Collection<MangaTag>) = tags.joinToString(separator = " ") { tag ->
		"tag:\"${tag.key}\""
	}
}
