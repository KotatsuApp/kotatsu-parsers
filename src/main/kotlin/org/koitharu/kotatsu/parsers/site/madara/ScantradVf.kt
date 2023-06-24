package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*

@MangaSourceParser("SCANTRADVF", "ScantradVf", "fr")
internal class ScantradVf(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.SCANTRADVF, "scantrad-vf.co") {

	override val datePattern = "d MMMM yyyy"

	override val tagPrefix = "genre/"

	override fun String.asMangaState(): MangaState? = when (this) {
		"En cours",
		-> MangaState.ONGOING

		"Complété",
		"Terminé",
		-> MangaState.FINISHED

		else -> null
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.selectFirstOrThrow(".post-content")
		val tags = postContent.getElementsContainingOwnText("Genre(s)")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			rating = postContent.selectFirstOrThrow(".post-rating")
				.selectFirstOrThrow(".total_votes").text().toFloat() / 5f,
			description = root.selectFirstOrThrow(".description-summary")
				.firstElementChild()?.html(),
			author = postContent.getElementsContainingOwnText("Auteur(s)")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Status")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/genre/action/").parseHtml()
		val body = doc.body()
		val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
		val root2 = body.selectFirst("div.genres_wrap")?.selectFirst("ul.list-unstyled")
		if (root1 == null && root2 == null) {
			doc.parseFailed("Root not found")
		}
		val list = root2?.select("li").orEmpty()
		val keySet = HashSet<String>(list.size)
		return list.mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			val href = a.attr("href").removeSuffix("/")
				.substringAfterLast(tagPrefix, "")
			if (href.isEmpty() || !keySet.add(href)) {
				return@mapNotNullToSet null
			}
			MangaTag(
				key = href,
				title = a.ownText().trim().ifEmpty {
					return@mapNotNullToSet null
				}.toTitleCase(),
				source = source,
			)
		}
	}
}
