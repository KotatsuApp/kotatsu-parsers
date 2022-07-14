package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANGAS_ORIGINES", "Mangas Origines", "fr")
internal class MangasOriginesParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAS_ORIGINES, "mangas-origines.fr") {

	override val tagPrefix = "catalogues-genre/"

	override fun getFaviconUrl(): String {
		return "https://${getDomain()}/wp-content/uploads/2020/11/Mangas-150x150.png"
	}

	override suspend fun getDetails(manga: Manga): Manga {
		return coroutineScope {
			val chapters = async { loadChapters(manga.url) }
			val body = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml().body()
			val root = body.selectFirstOrThrow(".site-content")
			val postContent = root.selectFirstOrThrow(".post-content")
			val tags = postContent.getElementsContainingOwnText("Genre")
				.firstOrNull()?.tableValue()
				?.getElementsByAttributeValueContaining("href", tagPrefix)
				?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
			manga.copy(
				largeCoverUrl = root.selectFirst("picture")
					?.selectFirst("img[data-src]")
					?.attrAsAbsoluteUrlOrNull("data-src"),
				description = (root.selectFirst(".detail-content")
					?: root.selectFirstOrThrow(".manga-excerpt")).html(),
				author = postContent.getElementsContainingOwnText("Auteur")
					.firstOrNull()?.tableValue()?.text()?.trim(),
				state = postContent.getElementsContainingOwnText("STATUS")
					.firstOrNull()?.tableValue()?.text()?.asMangaState(),
				tags = tags,
				isNsfw = body.hasClass("adult-content"),
				chapters = chapters.await(),
			)
		}
	}

	private fun Element.tableValue(): Element {
		for (p in parents()) {
			val children = p.children()
			if (children.size == 2) {
				return children[1]
			}
		}
		parseFailed("Cannot find tableValue for node ${text()}")
	}

	private fun String.asMangaState() = when (trim().lowercase(Locale.FRANCE)) {
		"en cours" -> MangaState.ONGOING
		"abandonné",
		"terminé",
		-> MangaState.FINISHED

		else -> null
	}

	private fun Element.asMangaTag() = MangaTag(
		title = ownText(),
		key = attr("href").removeSuffix('/').substringAfterLast('/')
			.replace('-', '+'),
		source = source,
	)

	private suspend fun loadChapters(mangaUrl: String): List<MangaChapter> {
		val url = mangaUrl.toAbsoluteUrl(getDomain()).removeSuffix('/') + "/ajax/chapters/"
		val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE)
		val doc = context.httpPost(url, emptyMap()).parseHtml()
		return doc.select("li.wp-manga-chapter").asReversed().mapChapters { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
			MangaChapter(
				id = generateUid(href),
				url = href,
				name = a.text(),
				number = i + 1,
				branch = null,
				uploadDate = dateFormat.tryParse(
					li.selectFirst(".chapter-release-date")?.text()?.trim(),
				),
				scanlator = null,
				source = source,
			)
		}
	}
}