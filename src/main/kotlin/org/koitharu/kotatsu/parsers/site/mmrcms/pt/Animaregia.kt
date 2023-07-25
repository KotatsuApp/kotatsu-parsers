package org.koitharu.kotatsu.parsers.site.mmrcms.pt


import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.site.madara.MmrcmsParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import java.util.Locale


@MangaSourceParser("ANIMAREGIA", "Animaregia", "pt")
internal class Animaregia(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.ANIMAREGIA, "animaregia.net") {

	override val selectdate = "div.col-md-4"
	override val sourceLocale: Locale = Locale.ENGLISH

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body().selectFirstOrThrow("ul.list-group")

		val chaptersDeferred = async { getChapters(manga, doc) }

		val desc = doc.select(selectdesc).let {
			if (it.select("p").text().isNotEmpty()) {
				it.select("p").joinToString(separator = "\n\n") { p ->
					p.text().replace("<br>", "\n")
				}
			} else {
				it.text()
			}
		}

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
					key = a.attr("href").substringAfterLast("/"),
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
