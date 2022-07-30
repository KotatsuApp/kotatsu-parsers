package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class Madara6Parser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
) : MadaraParser(context, source, domain) {

	override val datePattern: String = "dd MMMM yyyy"

	override suspend fun getDetails(manga: Manga): Manga {
		return coroutineScope {
			val chapters = async { loadChapters(manga.url) }
			val body = context.httpGet(manga.url.toAbsoluteUrl(getDomain())).parseHtml().body()
			parseDetails(manga, body, chapters.await())
		}
	}

	protected fun Element.tableValue(): Element {
		for (p in parents()) {
			val children = p.children()
			if (children.size == 2) {
				return children[1]
			}
		}
		parseFailed("Cannot find tableValue for node ${text()}")
	}

	protected abstract fun String.asMangaState(): MangaState?

	protected fun Element.asMangaTag() = MangaTag(
		title = ownText(),
		key = attr("href").removeSuffix('/').substringAfterLast('/')
			.replace('-', '+'),
		source = source,
	)

	protected open suspend fun loadChapters(mangaUrl: String): List<MangaChapter> {
		val url = mangaUrl.toAbsoluteUrl(getDomain()).removeSuffix('/') + "/ajax/chapters/"
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale ?: Locale.ROOT)
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

	protected abstract fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga
}