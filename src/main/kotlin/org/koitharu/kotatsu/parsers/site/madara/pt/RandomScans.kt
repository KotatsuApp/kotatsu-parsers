package org.koitharu.kotatsu.parsers.site.madara.pt

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("RANDOMSCANS", "Luratoon Scan", "pt")
internal class RandomScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RANDOMSCANS, "luratoon.com") {

	override val listUrl = "todas-as-obras/"

	override fun parseMangaList(doc: Document): List<Manga> {
		return doc.selectFirstOrThrow(".all__comics").select(".comic").map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("h2").text(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}
}
