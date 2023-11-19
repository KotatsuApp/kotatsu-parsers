package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("HENTAI3", "3Hentai", type = ContentType.HENTAI)
internal class Hentai3(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAI3, "3hentai.net") {

	override val selectGallery = ".doujin "
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = ".title"
	override val pathTagUrl = "/tags-popular/"
	override val selectTags = "div.tag-listing-container"
	override val selectTag = "div.tag-container:contains(Tags)"
	override val selectAuthor = "div.tag-container:contains(Artists) .filter-elem a"
	override val selectLanguageChapter = "div.tag-container:contains(Languages) a"
	override val selectUrlChapter = "#main-cover a"
	override val idImg = ".js-main-img"
	override val listLanguage = arrayOf(
		"/english",
		"/spanish",
		"/french",
		"/italian",
		"/portuguese",
		"/russian",
		"/japanese",
	)

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

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
				} else {
					append("/tags/")
					append(tag.key)
				}
				append("/")
				append(page)
				if (sortOrder == SortOrder.POPULARITY) {
					append("?sort=popular")
				}
			} else if (!query.isNullOrEmpty()) {
				append("/search?q=")
				append(query.urlEncoded())
				if (sortOrder == SortOrder.POPULARITY) {
					append("&sort=popular")
				}
				append("&page=")
				append(page)
			} else {
				append("/")
				append(page)
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow(idImg).src() ?: doc.parseFailed("Image src not found")
	}

	private fun buildQuery(tags: Collection<MangaTag>) =
		tags.joinToString(separator = " ") { tag ->
			if (tag.key == "languageKey") {
				"language:\"${tag.title.removePrefix("/")}\""
			} else {
				"tag:\"${tag.title}\""
			}
		}
}
