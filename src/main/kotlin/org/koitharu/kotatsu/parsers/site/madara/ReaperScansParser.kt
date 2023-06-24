package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("REAPER_SCANS_ID", "ReaperScansID", "in")
internal class ReaperScansParser(context: MangaLoaderContext) :
	Madara6Parser(context, MangaSource.REAPER_SCANS_ID, "reaperscans.id") {

	override val datePattern = "MMMM dd, yyyy"
	override val tagPrefix = "genre/"
	override val sourceLocale: Locale = Locale.ENGLISH

	override fun String.asMangaState(): MangaState? = when (this) {
		"OnGoing",
		"Upcoming",
		-> MangaState.ONGOING

		"Completed",
		"Dropped",
		-> MangaState.FINISHED

		else -> null
	}

	override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
		val root = body.selectFirstOrThrow(".site-content")
		val postContent = root.requireElementById("nav-info")
		val tags = postContent.getElementsContainingOwnText("Gênero")
			.firstOrNull()?.tableValue()
			?.getElementsByAttributeValueContaining("href", tagPrefix)
			?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
		return manga.copy(
			rating = postContent.selectFirstOrThrow(".post-rating")
				.selectFirstOrThrow(".total_votes").text().toFloat() / 5f,
			largeCoverUrl = root.selectFirst(".summary_image")
				?.selectFirst("img[data-src]")
				?.attrAsAbsoluteUrlOrNull("data-src")
				.assertNotNull("largeCoverUrl"),
			description = root.requireElementById("nav-profile")
				.selectFirstOrThrow(".description-summary")
				.firstElementChild()?.html(),
			author = postContent.getElementsContainingOwnText("Author(s)")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			altTitle = postContent.getElementsContainingOwnText("Alternative")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = postContent.getElementsContainingOwnText("Status")
				.firstOrNull()?.tableValue()?.text()?.asMangaState(),
			tags = tags,
			isNsfw = body.hasClass("adult-content"),
			chapters = chapters,
		)
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/semua-komik/").parseHtml()
		val body = doc.body()
		val root1 = body.selectFirst("header")?.selectFirst("ul.second-menu")
		val root2 = body.selectFirst("div.genres_wrap")?.selectFirst("ul.list-unstyled")
		if (root1 == null && root2 == null) {
			doc.parseFailed("Root not found")
		}
		val list = root1?.select("li").orEmpty() + root2?.select("li").orEmpty()
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
					a.selectFirst(".menu-image-title")?.text()?.trim() ?: return@mapNotNullToSet null
				}.toTitleCase(),
				source = source,
			)
		}
	}

}
