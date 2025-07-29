package org.koitharu.kotatsu.parsers.site.liliana.vi

import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("DOCTRUYEN5S", "DocTruyen5s", "vi")
internal class DocTruyen5s(context: MangaLoaderContext) :
	LilianaParser(context, MangaParserSource.DOCTRUYEN5S, "manga.io.vn", 42) {

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "no-referrer")
		.build()

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(const CHAPTER_ID)").data()
		val chapterId = script.substringAfter("const CHAPTER_ID = ", "").substringBefore(';', "")
		check(chapterId.isNotEmpty()) { ParseException("Không thể tìm thấy CHAPTER_ID, hãy kiểm tra nguồn!", fullUrl) }

		val ajaxUrl = buildString {
			append("https://")
			append(domain)
			append("/ajax/image/list/chap/")
			append(chapterId)
		}

		val responseJson = webClient.httpGet(ajaxUrl).parseJson()
		check(responseJson.getBooleanOrDefault("status", false)) { responseJson.getString("msg") }

		val pageListDoc = Jsoup.parse(responseJson.getString("html"))

		return pageListDoc.selectOrThrow("div.separator a").mapNotNull { element ->
			val originalUrl = element.attr("href").takeIf { it.isNotEmpty() } ?: element.attr("src")
			if (originalUrl.isEmpty()) return@mapNotNull null
			
			val workingUrl = addCdnServers(originalUrl).firstOrNull { url ->
				checkMangaImgs(url)
			}

			workingUrl?.let { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		}
	}

	private fun addCdnServers(url: String): List<String> {
		if (!url.startsWith("http")) return emptyList()
		
		val urlFinal = url.replace("https://", "")
		return listOf(
			url,
			"https://proxy.luce.workers.dev/$url",
			"https://images2-focus-opensocial.googleusercontent.com/gadgets/proxy?url=$url&container=focus&gadget=a&no_expand=1&resize_h=0&rewriteMime=image/*",
			"https://i0.wp.com/$urlFinal",
			"https://cdn.statically.io/img/$urlFinal"
		)
	}

	private suspend fun checkMangaImgs(url: String): Boolean {
		return try {
			val response = webClient.httpHead(url)
			val contentType = response.header("Content-Type") ?: ""
			contentType.startsWith("image/")
		} catch (e: Exception) {
			false
		}
	}
}
