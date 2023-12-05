package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.EnumSet

@MangaSourceParser("HENTAIENVY", "HentaiEnvy", type = ContentType.HENTAI)
internal class HentaiEnvy(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIENVY, "hentaienvy.com") {
	override val selectGalleryLink = "a"
	override val selectGalleryTitle = "div.title"
	override val selectTags = ".tags_items"
	override val selectTag = ".gt_right_tags ul:contains(Tags:)"
	override val selectAuthor = ".gt_right_tags ul:contains(Artists:) a"
	override val selectLanguageChapter = ".gt_right_tags ul:contains(Languages:) a"
	override val idImg = "fimg"
	override val listLanguage = arrayOf(
		"/english",
		"/french",
		"/japanese",
		"/chinese",
		"/spanish",
		"/russian",
		"/korean",
		"/german",
		"/portuguese",
	)

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/search/?s_key=")
					append(filter.query.urlEncoded())
					append("&")
				}

				is MangaListFilter.Advanced -> {
					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							if (it.key == "languageKey") {
								append("/language")
								append(it.title)
								append("/?")
							} else {
								append("/tag/")
								append(it.key)
								if (filter.sortOrder == SortOrder.POPULARITY) {
									append("/popular")
								}
								append("/?")
							}
						}
					} else {
						append("/?")
					}
				}

				null -> append("/?")
			}
			append("page=")
			append(page)

		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}
}
