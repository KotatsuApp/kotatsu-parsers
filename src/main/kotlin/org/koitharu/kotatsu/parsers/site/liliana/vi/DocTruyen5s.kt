package org.koitharu.kotatsu.parsers.site.liliana.vi

import org.jsoup.Jsoup
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser
import org.koitharu.kotatsu.parsers.util.json.getBooleanOrDefault
import org.koitharu.kotatsu.parsers.util.*

private const val SERVER_1 = "" // original src
private const val SERVER_2 = "https://proxy.luce.workers.dev/baseUrl"
private const val SERVER_3 = "https://images2-focus-opensocial.googleusercontent.com/gadgets/proxy?url=baseUrl&container=focus&gadget=a&no_expand=1&resize_h=0&rewriteMime=image/*"
private const val SERVER_4 = "https://i0.wp.com/baseUrl"
private const val SERVER_5 = "https://cdn.statically.io/img/baseUrl"

@MangaSourceParser("DOCTRUYEN5S", "DocTruyen5s", "vi")
internal class DocTruyen5s(context: MangaLoaderContext) :
	LilianaParser(context, MangaParserSource.DOCTRUYEN5S, "dongmoe.com", 42) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("dongmoe.com", "manga.io.vn")

	private val preferredServerKey = ConfigKey.PreferredImageServer(
		presetValues = mapOf(
			SERVER_1 to "Server 1",
			SERVER_2 to "Server 2",
			SERVER_3 to "Server 3",
			SERVER_4 to "Server 4",
			SERVER_5 to "Server 5"
		),
		defaultValue = SERVER_1,
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
		keys.add(preferredServerKey)
	}

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("referer", "no-referrer")
		.build()

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val script = doc.selectFirstOrThrow("script:containsData(const CHAPTER_ID)").data()
		val chapterId = script.substringAfter("const CHAPTER_ID = ", "").substringBefore(';', "")
		check(chapterId.isNotEmpty()) { "Không thể tìm thấy CHAPTER_ID, hãy kiểm tra nguồn!" }

		val ajaxUrl = buildString {
			append("https://")
			append(domain)
			append("/ajax/image/list/chap/")
			append(chapterId)
		}

		val responseJson = webClient.httpGet(ajaxUrl).parseJson()
		check(responseJson.getBooleanOrDefault("status", false)) { responseJson.getString("msg") }

		val pageListDoc = Jsoup.parse(responseJson.getString("html"))
		val preferredServer = config[preferredServerKey] ?: SERVER_1

		return pageListDoc.selectOrThrow("div.separator a").mapNotNull { element ->
			val originalUrl = element.attr("href").takeIf { it.isNotEmpty() } ?: element.attr("src")
			if (originalUrl.isEmpty()) return@mapNotNull null
			
			val imageUrl = when (preferredServer) {
				SERVER_1 -> originalUrl
				SERVER_2 -> SERVER_2.replace("baseUrl", originalUrl)
				SERVER_3 -> SERVER_3.replace("baseUrl", originalUrl)
				SERVER_4 -> SERVER_4.replace("baseUrl", originalUrl.replace("https://", ""))
				SERVER_5 -> SERVER_5.replace("baseUrl", originalUrl.replace("https://", ""))
				else -> originalUrl
			}
			
			if (checkMangaImgs(imageUrl)) {
				MangaPage(
					id = generateUid(imageUrl),
					url = imageUrl,
					preview = null,
					source = source,
				)
			} else {
				null
			}
		}
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
