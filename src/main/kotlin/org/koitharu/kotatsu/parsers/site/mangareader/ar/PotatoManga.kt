package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import org.koitharu.kotatsu.parsers.util.*

@Broken
@MangaSourceParser("POTATOMANGA", "PotatoManga", "ar")
internal class PotatoManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.POTATOMANGA, "potatomanga.xyz", pageSize = 30, searchPageSize = 10) {
	override val listUrl = "/series"
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow(selectTestScript)
		val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')'))
			.getJSONArray("sources")
			.getJSONObject(0)
			.getJSONArray("images")
		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			pages.add(
				MangaPage(
					id = generateUid(images.getString(i).replace("\\", "")),
					url = images.getString(i).replace("\\", ""),
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
