package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser
import org.koitharu.kotatsu.parsers.util.*
import java.util.Locale

@MangaSourceParser("MANHUASCAN", "ManhuaScan.io", "en")
internal class ManhuaScan(context: MangaLoaderContext) :
	MadthemeParser(context, MangaSource.MANHUASCAN, "manhuascan.io") {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "search"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append('/')
			append(listUrl)
			when (filter) {

				is MangaListFilter.Search -> {
					append("?sort=updated_at&q=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					append("?sort=")
					when (filter.sortOrder) {
						SortOrder.POPULARITY -> append("views")
						SortOrder.UPDATED -> append("updated_at")
						SortOrder.ALPHABETICAL -> append("name")
						SortOrder.NEWEST -> append("created_at")
						SortOrder.RATING -> append("rating")
						else -> append("updated_at")
					}
					if (filter.tags.isNotEmpty()) {
						filter.tags.forEach {
							append("&")
							append("include[]".urlEncoded())
							append("=")
							append(it.key)
						}
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "ongoing"
								MangaState.FINISHED -> "completed"
								else -> "all"
							},
						)
					}

				}

				null -> append("?sort=updated_at")
			}

			append("&page=")
			append(page.toString())
		}

		val doc = webClient.httpGet(url).parseHtml()

		return doc.select("div.book-item").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirstOrThrow("div.meta").selectFirst("div.title")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirstOrThrow("div.meta span.score").ownText().toFloatOrNull()?.div(5f)
					?: RATING_UNKNOWN,
				tags = doc.body().select("div.meta div.genres span").mapNotNullToSet { span ->
					MangaTag(
						key = span.attr("class"),
						title = span.text().toTitleCase(),
						source = source,
					)
				},
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		val script = docs.selectFirstOrThrow("script:containsData(var chapImages)")
		val images = script.data().substringAfter("= \"").substringBefore("\";").split(",")
		return images.map {
			MangaPage(
				id = generateUid(it),
				url = it,
				preview = null,
				source = source,
			)
		}
	}

}
