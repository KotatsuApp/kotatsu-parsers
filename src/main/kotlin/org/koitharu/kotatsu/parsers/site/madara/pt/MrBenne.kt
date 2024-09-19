package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toTitleCase

@MangaSourceParser("MRBENNE", "MrBenne", "pt", ContentType.HENTAI)
internal class MrBenne(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MRBENNE, "mrbenne.com", 10) {
	override val datePattern: String = "dd/MM/yyyy"

	override suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/?s=&post_type=wp-manga").parseHtml()
		val body = doc.body()
		val root = body.selectFirst("div.form-group.checkbox-group")
		val list = root?.select("div.checkbox").orEmpty()
		val keySet = HashSet<String>(list.size)
		return list.mapNotNullToSet { div ->
			val input = div.selectFirst("input") ?: return@mapNotNullToSet null
			val label = div.selectFirst("label") ?: return@mapNotNullToSet null
			val tag = input.attr("value")
			if (tag.isEmpty() || !keySet.add(tag)) {
				return@mapNotNullToSet null
			}
			MangaTag(
				key = tag,
				title = label.ownText().ifEmpty {
					tag
				}.toTitleCase(sourceLocale),
				source = source,
			)
		}
	}
}
