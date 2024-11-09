package org.koitharu.kotatsu.parsers.site.madara.en

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat

@MangaSourceParser("MANHWAHENTAI", "ManhwaHentai", "en", ContentType.HENTAI)
internal class ManhwaHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWAHENTAI, "manhwahentai.me", 20) {
	override val tagPrefix = "webtoon-genre/"
	override val listUrl = "webtoon/"
	override val withoutAjax = true

	override val postReq = true

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()
		val chaptersDeferred = async { loadChapters(manga.url, doc) }
		val desc = body.select(selectDesc).html()
		val stateDiv = if (selectState.isEmpty()) {
			(body.selectFirst("div.post-content_item:contains(Status)")
				?: body.selectFirst("div.post-content_item:contains(Statut)")
				?: body.selectFirst("div.post-content_item:contains(État)")
				?: body.selectFirst("div.post-content_item:contains(حالة العمل)")
				?: body.selectFirst("div.post-content_item:contains(Estado)")
				?: body.selectFirst("div.post-content_item:contains(สถานะ)")
				?: body.selectFirst("div.post-content_item:contains(Stato)")
				?: body.selectFirst("div.post-content_item:contains(Durum)")
				?: body.selectFirst("div.post-content_item:contains(Statüsü)")
				?: body.selectFirst("div.post-content_item:contains(Статус)")
				?: body.selectFirst("div.post-content_item:contains(状态)")
				?: body.selectFirst("div.post-content_item:contains(الحالة)"))?.selectLast("div.summary-content")
		} else {
			body.selectFirst(selectState)
		}


		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			}
		}

		val alt =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()
				?.textOrNull()
				?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
					.firstOrNull()?.tableValue()?.textOrNull()

		manga.copy(
			tags = doc.body().select(selectGenre).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	override suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {
		val doc = if (postReq) {
			val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
			val url = "https://$domain/wp-admin/admin-ajax.php"
			val postData = "action=ajax_chap&post_id=$mangaId"
			webClient.httpPost(url, postData).parseHtml()
		} else {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
			webClient.httpPost(url, emptyMap()).parseHtml()
		}
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = i + 1f,
				volume = 0,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				scanlator = null,
				source = source,
			)
		}
	}
}
