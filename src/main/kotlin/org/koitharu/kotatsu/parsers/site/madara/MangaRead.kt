package org.koitharu.kotatsu.parsers.site.madara

import androidx.collection.arraySetOf
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("MANGAREAD", "MangaRead", "en")
internal class MangaRead(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAREAD, "www.mangaread.org") {

	override val tagPrefix = "genres/"
	override val datePattern = "dd.MM.yyyy"
	private val nsfwTags = arraySetOf("yaoi", "yuri", "mature")

	init {
		paginator.firstPage = 1
		searchPaginator.firstPage = 1
	}

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.POPULARITY,
	)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = urlBuilder()
			.addPathSegment("page")
			.addPathSegment(page.toString())
			.addQueryParameter("s", query.orEmpty())
			.addQueryParameter("post_type", "wp-manga")
		tags?.forEachIndexed { index, tag ->
			url.addQueryParameter("genre[$index]", tag.key)
		}
		url.addQueryParameter("op", "")
			.addQueryParameter("author", "")
			.addQueryParameter("artist", "")
			.addQueryParameter("release", "")
			.addQueryParameter("adult", "")
		if (query.isNullOrEmpty()) {
			url.addQueryParameter(
				"&m_orderby=",
				when (sortOrder) {
					SortOrder.RATING -> "trending"
					SortOrder.ALPHABETICAL -> "alphabet"
					SortOrder.POPULARITY -> "views"
					SortOrder.NEWEST -> "new-manga"
					SortOrder.UPDATED -> "latest"
				},
			)
		}
		val root = webClient.httpGet(url.build()).parseHtml().body().selectFirstOrThrow(".search-wrap")
		return root.select(".c-tabs-item__content").map { div ->
			val a = div.selectFirstOrThrow("a")
			val img = div.selectLastOrThrow("img")
			val href = a.attrAsRelativeUrl("href")
			val postContent = root.selectFirstOrThrow(".post-content")
			val tagSet = postContent.getElementsContainingOwnText("Genre")
				.firstOrNull()?.tableValue()
				?.getElementsByAttributeValueContaining("href", tagPrefix)
				?.mapToSet { it.asMangaTag() }.orEmpty()
			Manga(
				id = generateUid(href),
				title = a.attr("title"),
				altTitle = postContent.getElementsContainingOwnText("Alternative")
					.firstOrNull()?.tableValue()?.text()?.trim(),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				coverUrl = img.src().orEmpty(),
				author = postContent.getElementsContainingOwnText("Author")
					.firstOrNull()?.tableValue()?.text()?.trim(),
				state = postContent.getElementsContainingOwnText("Status")
					.firstOrNull()?.tableValue()?.text()?.asMangaState(),
				isNsfw = isNsfw(tagSet),
				rating = div.selectFirstOrThrow(".score").text()
					.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
				tags = tagSet,
				source = source,
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

	private fun isNsfw(tags: Set<MangaTag>): Boolean {
		return tags.any { it.key in nsfwTags }
	}

	private fun String.asMangaState() = when (trim().lowercase(sourceLocale)) {
		"ongoing" -> MangaState.ONGOING
		"completed" -> MangaState.FINISHED
		else -> null
	}

	private fun Element.asMangaTag() = MangaTag(
		title = ownText(),
		key = attr("href").removeSuffix('/').substringAfterLast('/')
			.replace('-', '+'),
		source = source,
	)
}
