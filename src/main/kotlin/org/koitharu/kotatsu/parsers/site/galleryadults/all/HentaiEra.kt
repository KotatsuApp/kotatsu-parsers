package org.koitharu.kotatsu.parsers.site.galleryadults.all

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.galleryadults.GalleryAdultsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("HENTAIERA", "HentaiEra", type = ContentType.HENTAI)
internal class HentaiEra(context: MangaLoaderContext) :
	GalleryAdultsParser(context, MangaSource.HENTAIERA, "hentaiera.com", 25) {
	override val selectTags = ".tags_section"
	override val selectTag = ".galleries_info li:contains(Tags) div.info_tags"
	override val selectAuthor = ".galleries_info li:contains(Artists) span.item_name"
	override val selectLanguageChapter = ".galleries_info li:contains(Languages) div.info_tags .item_name"
	override val listLanguage = arrayOf(
		"/english",
		"/japanese",
		"/spanish",
		"/french",
		"/korean",
		"/german",
		"/russian",
	)

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override fun Element.parseTags() = select("a.tag, .gallery_title a").mapToSet {
		val key = it.attr("href").removeSuffix('/').substringAfterLast('/')
		val name = it.selectFirst(".item_name")?.text() ?: it.text()
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

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
					append("/tag/")
					append(tag.key)
				}
				append("/")
				if (sortOrder == SortOrder.POPULARITY) {
					append("popular/")
				}
				append("?")
			} else if (!query.isNullOrEmpty()) {
				append("/search/?key=")
				if (sortOrder == SortOrder.POPULARITY) {
					append(query.replace("&lt=1&dl=0&pp=0&tr=0", "&lt=0&dl=0&pp=1&tr=0"))
				} else {
					append(query)
				}
				append("&")
			} else {
				append("/?")
			}
			append("page=")
			append(page)
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun buildQuery(tags: Collection<MangaTag>): String {
		val queryDefault =
			"&search=&mg=1&dj=1&ws=1&is=1&ac=1&gc=1&en=0&jp=0&es=0&fr=0&kr=0&de=0&ru=0&lt=1&dl=0&pp=0&tr=0"
		var tag = ""
		var queryMod = ""
		tags.map {
			if (it.key == "languageKey" && it.title == "/english") {
				queryMod = queryDefault.replace("en=0", "en=1")
			}
			if (it.key == "languageKey" && it.title == "/japanese") {
				queryMod = queryDefault.replace("jp=0", "jp=1")
			}
			if (it.key == "languageKey" && it.title == "/spanish") {
				queryMod = queryDefault.replace("es=0", "es=1")
			}
			if (it.key == "languageKey" && it.title == "/french") {
				queryMod = queryDefault.replace("fr=0", "fr=1")
			}
			if (it.key == "languageKey" && it.title == "/korean") {
				queryMod = queryDefault.replace("kr=0", "kr=1")
			}
			if (it.key == "languageKey" && it.title == "/russian") {
				queryMod = queryDefault.replace("ru=0", "ru=1")
			}
			if (it.key == "languageKey" && it.title == "/german") {
				queryMod = queryDefault.replace("de=0", "de=1")
			}
			if (it.key != "languageKey") {
				tag += it.key + " "
			}
		}

		if (queryMod.isEmpty()) {
			queryMod = "&search=&mg=1&dj=1&ws=1&is=1&ac=1&gc=1&en=1&jp=1&es=1&fr=1&kr=1&de=1&ru=1&lt=1&dl=0&pp=0&tr=0"
		}
		return tag + queryMod
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val urlChapters = doc.selectFirstOrThrow("#cover a, .cover a, .left_cover a").attr("href")
		val tag = doc.selectFirst(selectTag)?.parseTags()
		val branch = doc.select(selectLanguageChapter).joinToString(separator = " / ") {
			it.text()
		}
		return manga.copy(
			tags = tag.orEmpty(),
			author = doc.selectFirst(selectAuthor)?.text(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1,
					url = urlChapters,
					scanlator = null,
					uploadDate = 0,
					branch = branch,
					source = source,
				),
			),
		)
	}
}
