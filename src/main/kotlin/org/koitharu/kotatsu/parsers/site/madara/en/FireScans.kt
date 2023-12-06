package org.koitharu.kotatsu.parsers.site.madara.en

import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("FIRESCANS", "FireScans", "en")
internal class FireScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FIRESCANS, "firescans.xyz", 10) {

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
		}.map { div ->
			val href = div.selectFirst("h3 a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4")
				?: div.selectFirst(".manga-name"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					in abandoned -> MangaState.ABANDONED
					in paused -> MangaState.PAUSED
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapterProtector = doc.requireElementById("chapter-protector-data")
		val chapterProtectorHtml =
			context.decodeBase64(chapterProtector.attr("src").removePrefix("data:text/javascript;base64,"))
				.toString(Charsets.UTF_8)
		val password = chapterProtectorHtml.substringAfter("wpmangaprotectornonce='").substringBefore("';")
		val chapterData = JSONObject(
			chapterProtectorHtml.substringAfter("chapter_data='").substringBefore("';").replace("\\/", "/"),
		)
		val unsaltedCiphertext = context.decodeBase64(chapterData.getString("ct"))
		val salt = chapterData.getString("s").toString().decodeHex()
		val ciphertext = "Salted__".toByteArray(Charsets.UTF_8) + salt + unsaltedCiphertext
		val rawImgArray = CryptoAES(context).decrypt(context.encodeBase64(ciphertext), password)
		val imgArrayString = rawImgArray.filterNot { c -> c == '[' || c == ']' || c == '\\' || c == '"' }

		return imgArrayString.split(",").map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	fun String.decodeHex(): ByteArray {
		check(length % 2 == 0) { "Must have an even length" }

		return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
	}

}
