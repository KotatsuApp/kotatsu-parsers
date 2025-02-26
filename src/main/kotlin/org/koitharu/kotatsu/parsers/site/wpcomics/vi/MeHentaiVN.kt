package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MEHENTAIVN", "MeHentaiVN", "vi", ContentType.HENTAI)
internal class MeHentaiVN(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.MEHENTAIVN, "www.mehentaivn.xyz", 44) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("www.mehentaivn.xyz", "www.hentaivnx.autos")

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet {
			val tagTitle = it.text()
			if (tagTitle.isNotEmpty())
				MangaTag(
					title = tagTitle.toTitleCase(sourceLocale),
					key = tagsElement.attr("href").substringAfterLast('/').trim(),
					source = source,
				)
			else null
		}
		val author = doc.body().selectFirst(selectAut)?.textOrNull()

		manga.copy(
			description = doc.selectFirst(selectDesc)?.html(),
			altTitle = doc.selectFirst("h2.other-name")?.textOrNull(),
			authors = author?.let { setOf(it) } ?: emptySet(),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await(),
			contentRating = ContentRating.ADULT,
		)
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/").parseHtml()
		val tagItems = doc.select("ul.dropdown-menu.megamenu li a")
		val tagSet = ArraySet<MangaTag>(tagItems.size)
		for (item in tagItems) {
			val title = item.attr("data-title").toTitleCase(sourceLocale)
			val key = item.attr("href").substringAfterLast('/').trim()
			if (key.isNotEmpty() && title.isNotEmpty()) {
				tagSet.add(MangaTag(title = title, key = key, source = source))
			}
		}
		return tagSet
	}
}
