package org.koitharu.kotatsu.parsers.site.mangabox.en


import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mangabox.MangaboxParser
import org.koitharu.kotatsu.parsers.util.*


@MangaSourceParser("MANGAIRO", "Mangairo", "en")
internal class Mangairo(context: MangaLoaderContext) :
	MangaboxParser(context, MangaSource.MANGAIRO) {

	override val configKeyDomain = ConfigKey.Domain("w.mangairo.com", "chap.mangairo.com")

	override val otherDomain = "chap.mangairo.com"

	override val datePattern = "MMM-dd-yy"
	override val listUrl = "/manga-list"
	override val searchUrl = "/list/search/"

	override val selectDesc = "div#story_discription p"
	override val selectState = "ul.story_info_right li:contains(Status) a"
	override val selectAlt = "ul.story_info_right li:contains(Alter) h2"
	override val selectAut = "ul.story_info_right li:contains(Author) a"
	override val selectTag = "ul.story_info_right li:contains(Genres) a"

	override val selectChapter = "div.chapter_list li"
	override val selectDate = "p"

	override val selectPage = "div.panel-read-story img"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			if (!query.isNullOrEmpty()) {
				append(searchUrl)
				append(query.urlEncoded())
				append("?page=")
				append(page.toString())


			} else {

				append("$listUrl/")

				append("/type-")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("topview")
					SortOrder.UPDATED -> append("latest")
					SortOrder.NEWEST -> append("newest")
					else -> append("latest")
				}

				if (!tags.isNullOrEmpty()) {
					append("/ctg-")
					for (tag in tags) {
						append(tag.key)
					}
				} else {
					append("/ctg-all")
				}
				append("/state-all/page-")
				append(page.toString())
			}
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.story-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (div.selectFirst("h2")?.text() ?: div.selectFirst("h3")?.text()).orEmpty(),
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

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl/type-latest/ctg-all/state-all/page-1").parseHtml()
		return doc.select("div.panel_category a:not(.ctg_select)").mapNotNullToSet { a ->
			val key = a.attr("href").substringAfterLast("ctg-").substringBefore("/")
			val name = a.attr("title").replace("Category ", "")
			MangaTag(
				key = key,
				title = name,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = doc.selectFirstOrThrow(selectDesc).html()

		val stateDiv = doc.select(selectState).text()

		val state = stateDiv.let {
			when (it) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt = doc.body().select(selectAlt).text().replace("Alternative : ", "")

		val aut = doc.body().select(selectAut).eachText().joinToString()

		manga.copy(
			tags = doc.body().select(selectTag).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href")
						.substringAfterLast("page-"), // Yes the site, it's crashing between page is tag id
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = aut,
			state = state,
			chapters = chaptersDeferred.await(),
			isNsfw = manga.isNsfw,
		)
	}


}
