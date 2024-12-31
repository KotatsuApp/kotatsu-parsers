package org.koitharu.kotatsu.parsers.site.wpcomics.vi

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.wpcomics.WpComicsParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("NETTRUYEN", "NetTruyen", "vi")
internal class NetTruyen(context: MangaLoaderContext) :
	WpComicsParser(context, MangaParserSource.NETTRUYEN, "nettruyenrr.com", 44) {
	
	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("nettruyenrr.com", "nettruyenww.com", "nettruyenx.com")

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(doc) }
		val tagMap = getOrCreateTagMap()
		val tagsElement = doc.select("li.kind p.col-xs-8 a")
		val mangaTags = tagsElement.mapNotNullToSet { tagMap[it.text()] }
		manga.copy(
			description = doc.selectFirst(selectDesc)?.html().orEmpty(),
			altTitle = doc.selectFirst("h2.other-name")?.text().orEmpty(),
			author = doc.body().select(selectAut).text(),
			state = doc.selectFirst(selectState)?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			},
			tags = mangaTags,
			rating = doc.selectFirst("div.star input")?.attr("value")?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
			chapters = chaptersDeferred.await().reversed(),
		)
	}
}
