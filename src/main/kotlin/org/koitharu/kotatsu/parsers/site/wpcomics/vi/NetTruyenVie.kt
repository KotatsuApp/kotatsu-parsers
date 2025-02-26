package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("NETTRUYENVIE", "NetTruyenVie", "vi")
internal class NetTruyenVie(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENVIE, "nettruyenvie.com", 36) {

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val docDeferred = async { webClient.httpGet(fullUrl).parseHtml() }
		val chaptersDeferred = async { fetchChapters(manga.url) }
		val tagMap = getOrCreateTagMap()
		val doc = docDeferred.await()
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet { tagMap[it.text()] }
		val author = doc.body().select(selectAut).textOrNull()
		manga.copy(
			description = doc.selectFirst("div.detail-content > div")?.html(),
			altTitle = doc.selectFirst("h2.other-name")?.textOrNull(),
			authors = author?.let { setOf(it) } ?: emptySet(),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await(),
		)
	}

	private suspend fun fetchChapters(mangaUrl: String): List<MangaChapter> {
		val slug = mangaUrl.substringAfterLast('/')
		val chaptersUrl = "/Comic/Services/ComicService.asmx/ChapterList?slug=$slug".toAbsoluteUrl(domain)
		val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

		val data = webClient.httpGet(chaptersUrl).parseJson().getJSONArray("data")
		return List(data.length()) { i ->
			val jo = data.getJSONObject(data.length() - 1 - i)
			val chapterSlug = jo.getString("chapter_slug")
			val chapterUrl = "/truyen-tranh/$slug/$chapterSlug"

			MangaChapter(
				id = generateUid(chapterUrl),
				name = jo.getString("chapter_name"),
				number = i + 1f,
				volume = 0,
				url = chapterUrl,
				scanlator = null,
				uploadDate = df.tryParse(jo.getString("updated_at")),
				branch = null,
				source = source,
			)
		}
	}
}
