package org.koitharu.kotatsu.parsers.site.wpcomics.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet

@MangaSourceParser("XOXOCOMICS", "Xoxo Comics", "vi", ContentType.COMICS)
internal class XoxoComics(context: MangaLoaderContext) :
	WpComicsParser(context, MangaSource.XOXOCOMICS, "xoxocomics.net", 50){

	override val listUrl = "/genre"
	override val datePattern = "MM/dd/yyyy"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			if(!query.isNullOrEmpty()){
				append("/search?keyword=")
				append(query.urlEncoded())
				append("&page=")
				append(page.toString())
			}else
			{
			append(listUrl)
			if(!tags.isNullOrEmpty()){
				append("/")
				for (tag in tags) {
					append(tag.key)
				}
			}

			append("/")
			when (sortOrder) {
				SortOrder.POPULARITY -> append("popular")
				SortOrder.UPDATED -> append("")
				SortOrder.NEWEST -> append("newest")
				SortOrder.ALPHABETICAL -> append("alphabet")
				else -> append("")
			}

			append("?page=")
			append(page.toString())

			}
		}
		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h3").text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain) + "/all"
		val doc = webClient.httpGet(fullUrl).parseHtml()


		return doc.select(selectPage).map { url ->
			val img = url.src()?.toRelativeUrl(domain) ?: url.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(img),
				url = img,
				preview = null,
				source = source,
			)
		}
	}

}
