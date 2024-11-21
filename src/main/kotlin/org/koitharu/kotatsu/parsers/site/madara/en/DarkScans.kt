package org.koitharu.kotatsu.parsers.site.madara.en

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("DARK_SCANS", "DarkScans", "en")
internal class DarkScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DARK_SCANS, "darkscans.net", 18) {
	override val listUrl = "mangas/"
	override val tagPrefix = "mangas-genre/"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapterProtector = doc.getElementById("chapter-protector-data")
		if (chapterProtector == null) {
			throw if (doc.selectFirst(selectRequiredLogin) != null) {
				AuthRequiredException(source)
			} else {
				val root = doc.body().selectFirst(selectBodyPage) ?: throw ParseException(
					"No image found, try to log in",
					fullUrl,
				)
				return root.select(selectPage).map { div ->
					val img = div.selectFirstOrThrow("img")
					val url = img.requireSrc().toRelativeUrl(domain)
					MangaPage(
						id = generateUid(url),
						url = url.replace("http:", "https:"),
						preview = null,
						source = source,
					)
				}
			}
		} else {

			val chapterProtectorHtml = chapterProtector.attr("src")
				.takeIf { it.startsWith("data:text/javascript;base64,") }
				?.substringAfter("data:text/javascript;base64,")
				?.let {
					Base64.getDecoder().decode(it).decodeToString()
				}
				?: chapterProtector.html()

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
					url = url.replace("http:", "https:"),
					preview = null,
					source = source,
				)
			}

		}
	}

	private fun String.decodeHex(): ByteArray {
		check(length % 2 == 0) { "Must have an even length" }

		return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
	}
}
