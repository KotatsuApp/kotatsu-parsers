package org.koitharu.kotatsu.parsers.site.mmrcms.pt

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("ANIMAREGIA", "Animaregia", "pt")
internal class Animaregia(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.ANIMAREGIA, "animaregia.net") {

	override val selectDate = "div.col-md-4"
	override val sourceLocale: Locale = Locale.ENGLISH

	//temporary
	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body().selectFirstOrThrow("ul.list-group")
		val chaptersDeferred = async { getChapters(manga, doc) }
		val desc = doc.select(selectDesc).text()
		val stateDiv = body.selectFirst("li.list-group-item:contains(Status)")?.lastElementChild()
		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}
		val auth = doc.body().selectFirst("li.list-group-item:contains(Autor(es)) a")?.text()
		val tags = doc.body().select("li.list-group-item:contains(Autor(es)) a") ?: emptySet()
		manga.copy(
			tags = tags.mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = auth,
			description = desc,
			altTitle = null,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}
}
