package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase

@MangaSourceParser("MANGALINK_AR", "Mangalink", "ar")
internal class MangalinkParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALINK_AR, "mangalink.online") {

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(getDomain())
		val doc = context.httpGet(fullUrl).parseHtml()
		val chaptersDeferred = async { getChapters(manga, doc) }
		val root = doc.body().selectFirst("div.profile-manga")
			?.selectFirst("div.summary_content")
			?.selectFirst("div.post-content")
			?: throw ParseException("Root not found", fullUrl)
		val root2 = doc.body().selectFirst("div.content-area")
			?.selectFirst("div.c-page")
			?: throw ParseException("Root2 not found", fullUrl)
		manga.copy(
			tags = root.selectFirst("div.genres-content")?.select("a")
				?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
						title = a.text().toTitleCase(),
						source = source,
					)
				} ?: manga.tags,
			description = root2.selectFirst("div.description-summary")
				?.selectFirst("div.summary__content")
				?.select("p")
				?.filterNot { it.ownText().startsWith("A brief description") }
				?.joinToString { it.html() },
			chapters = chaptersDeferred.await(),
		)
	}

	override fun getFaviconUrl(): String =
		"https://cdn.${getDomain()}/wp-content/uploads/2020/05/cropped-mangalink-180x180.jpg"
}