package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import java.text.SimpleDateFormat

@MangaSourceParser("NETTRUYENX", "NetTruyenX", "vi")
internal class NetTruyenX(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYENX, "nettruyenx.net", 36) {

    override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "https://$domain/")
		.build()

    override val selectDesc = "div.detail-content div.shortened"
    override val selectState = "li.status p.col-xs-8"
    override val selectAut = "li.author p.col-xs-8"

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
			description = doc.selectFirst(selectDesc)?.html(),
			altTitles = setOfNotNull(doc.selectFirst("h2.other-name")?.textOrNull()),
			authors = setOfNotNull(author),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input[name=score]")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
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
			val chapterId = jo.getString("chapter_id")
			val chapterUrl = "/truyen-tranh/$slug/$chapterSlug/$chapterId"

			MangaChapter(
				id = generateUid(chapterUrl),
				title = jo.getStringOrNull("chapter_name"),
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
