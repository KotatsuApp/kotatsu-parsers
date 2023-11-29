package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("HENTAIFOX", "HentaiFox", type = ContentType.HENTAI)
internal class HentaiFox(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIFOX, "hentaifox.com") {
	override val selectGallery = ".lc_galleries .thumb, .related_galleries .thumb"
	override val pathTagUrl = "/tags/popular/pag/"
	override val selectTags = ".list_tags"
	override val selectTag = "ul.tags"
	override val selectLanguageChapter = "ul.languages a.tag_btn"
	override val listLanguage = arrayOf(
		"/english",
		"/french",
		"/japanese",
		"/chinese",
		"/spanish",
		"/russian",
		"/korean",
		"/indonesian",
		"/italian",
		"/portuguese",
		"/turkish",
		"/thai",
		"/vietnamese",
	)

	override val isMultipleTagsSupported = true

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/search/?q=")
					append(filter.query.urlEncoded())
					if (page > 1) {
						append("&page=")
						append(page.toString())
					}
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty() && filter.tags.size > 1) {
						append("/search/?q=")
						append(buildQuery(filter.tags))
						if (page > 1) {
							append("&page=")
							append(page.toString())
						}

						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("&sort=popular")
						}
					} else if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							if (it.key == "languageKey") {
								append("/language")
								append(it.title)
							} else {
								append("/tag/")
								append(it.key)
							}
						}
						append("/")
						if (filter.sortOrder == SortOrder.POPULARITY) {
							append("popular/")
						}

						if (page > 1) {
							append("/pag/")
							append(page.toString())
							append("/")
						}
					} else {
						if (page > 2) {
							append("/pag/")
							append(page.toString())
							append("/")
						} else if (page > 1) {
							append("/page/")
							append(page.toString())
							append("/")
						}
					}
				}

				null -> {
					if (page > 2) {
						append("/pag/")
						append(page.toString())
						append("/")
					} else if (page > 1) {
						append("/page/")
						append(page.toString())
						append("/")
					}
				}
			}
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	override fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".list_tag")?.text() ?: it.html().substringBefore("<")
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

	private fun buildQuery(tags: Collection<MangaTag>) =
		tags.joinToString(separator = " ") { tag ->
			if (tag.key == "languageKey") {
				tag.title.removePrefix("/")
			} else {
				tag.key
			}
		}
}
