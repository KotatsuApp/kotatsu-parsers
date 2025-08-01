package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.Locale

@MangaSourceParser("MANHWABREAKUP", "ManhwaBreakup", "th")
internal class ManhwaBreakup(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWABREAKUP, "www.manhwabreakup.com") {

	override val datePattern = "MMMM dd, yyyy"
	override val sourceLocale: Locale = Locale("th")
	override val withoutAjax = true
	override val postReq = false

	override val selectBodyPage = ".reading-content"
	override val selectPage = "img, div.displayImage + script:containsData(p,a,c,k,e,d)"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val root = doc.body().selectFirst(selectBodyPage) ?: doc.parseFailed("No content found")

		val pages = mutableListOf<MangaPage>()

		root.select(selectPage).forEachIndexed { index, element ->
			when (element.tagName()) {
				"img" -> {
					val url = element.requireSrc().toRelativeUrl(domain)
					pages.add(
						MangaPage(
							id = generateUid(url),
							url = url,
							preview = null,
							source = source,
						),
					)
				}

				"script" -> {
					val scriptData = element.data()
					if (scriptData.contains("eval(function(p,a,c,k,e,d)")) {
						val unpackedScript = unpackScript(scriptData)

						val imageUrl = unpackedScript
							.substringAfter("url(", "")
							.substringBefore(");", "")
							.trim()
							.removeSurrounding("'", "'")
							.removeSurrounding("\"", "\"")

						if (imageUrl.isNotEmpty()) {
							val blockWidth = Regex("""width:\s*"?\s*\+?\s*(\d+)\s*\+?\s*"?px;""")
								.find(unpackedScript)?.groupValues?.get(1)?.toIntOrNull() ?: 0

							val blockHeight = Regex("""height:\s*"?\s*\+?\s*(\d+)\s*\+?\s*"?px;""")
								.find(unpackedScript)?.groupValues?.get(1)?.toIntOrNull() ?: 0

							val matrixData = unpackedScript
								.substringAfter("[", "")
								.substringBefore("];", "")
								.let { "[$it]" }

							val scramblingData = buildString {
								append(blockWidth)
								append("|")
								append(blockHeight)
								append("|")
								append(matrixData)
							}

							val urlWithData = "$imageUrl#$scramblingData"

							pages.add(
								MangaPage(
									id = generateUid(urlWithData),
									url = urlWithData,
									preview = null,
									source = source,
								),
							)
						}
					}
				}
			}
		}

		return pages
	}

	private fun unpackScript(packedScript: String): String {
		val regex = Regex(
			"""evalKATEX_INLINE_OPENfunctionKATEX_INLINE_OPENp,a,c,k,e,(?:r|d)KATEX_INLINE_CLOSE.*?KATEX_INLINE_OPEN'(.+?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.+?)'\.splitKATEX_INLINE_OPEN'\|'KATEX_INLINE_CLOSE""",
			RegexOption.DOT_MATCHES_ALL,
		)
		val match = regex.find(packedScript) ?: return ""

		val (payload, radix, _, words) = match.destructured
		val wordList = words.split('|')

		var unpacked = payload
		for (i in wordList.indices.reversed()) {
			val word = wordList.getOrNull(i) ?: ""
			if (word.isNotEmpty()) {
				val pattern = "\\b${i.toString(radix.toInt())}\\b"
				unpacked = unpacked.replace(Regex(pattern), word)
			}
		}
		return unpacked
	}

	override suspend fun getFavicons(): Favicons {
		return Favicons(
			listOf(
				Favicon("https://$domain/wp-content/uploads/2022/03/break-up-logo1.png", 160, null),
			),
			domain,
		)
	}

}
