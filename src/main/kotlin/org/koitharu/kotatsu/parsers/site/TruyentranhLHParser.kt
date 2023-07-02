package org.koitharu.kotatsu.parsers.site

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlBuilder
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale

@MangaSourceParser("TRUYENTRANHLH", "TruyentranhLH", "vi")
class TruyentranhLHParser(context: MangaLoaderContext) :
	PagedMangaParser(context, source = MangaSource.TRUYENTRANHLH, pageSize = 18) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("truyentranhlh.net")
	override val sortOrders: Set<SortOrder> = EnumSet.allOf(SortOrder::class.java)

	private val mutex = Mutex()
	private var tagCache: Map<String, MangaTag>? = null

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val infoHeaderEl = docs.selectFirst("main.section-body")
		val infoEl = docs.selectFirst("main.section-body .series-information")
		val tags = infoEl?.select(".info-item:contains(Thể loại) > .info-value > a")?.mapNotNullToSet {
			getOrCreateTagMap()[it.text().trim()]
		}
		val state = when (infoEl?.selectFirst(".info-item:contains(Tình trạng) > .info-value")?.text()) {
			"Đang tiến hành" -> MangaState.ONGOING
			"Đã hoàn thành" -> MangaState.FINISHED
			else -> null
		}
		val rating = infoHeaderEl?.let {
			val like = it.selectFirst("#like .block.feature-name")?.text()?.toIntOrNull()
			val disLike = it.selectFirst("#dislike .block.feature-name")?.text()?.toIntOrNull()
			when {
				like == null || disLike == null -> RATING_UNKNOWN
				like == 0 && disLike == 0 -> RATING_UNKNOWN
				else -> like.toFloat() / (like + disLike)
			}
		}
		val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

		return manga.copy(
			altTitle = infoEl?.selectFirst(".info-item:contains(Tên khác) > .info-value")?.text(),
			author = infoEl?.select(".info-item:contains(Tác giả) > .info-value")?.joinToString { it.text() },
			tags = tags ?: emptySet(),
			state = state,
			rating = rating ?: RATING_UNKNOWN,
			description = infoHeaderEl?.selectFirst(".series-summary .summary-content")?.html(),
			chapters = docs.select("ul.list-chapters.at-series > a").mapChapters(reversed = true) { index, element ->
				MangaChapter(
					id = generateUid(element.attrAsRelativeUrl("href")),
					name = element.selectFirst(".chapter-name")?.text()?.trim().orEmpty(),
					number = index + 1,
					url = element.attrAsRelativeUrl("href"),
					scanlator = null,
					uploadDate = chapterDateFormat.tryParse(element.selectFirst(".chapter-time")?.text()),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val sortQuery = when (sortOrder) {
			SortOrder.UPDATED -> "update"
			SortOrder.NEWEST -> "new"
			SortOrder.RATING -> "like"
			SortOrder.POPULARITY -> "top"
			SortOrder.ALPHABETICAL -> "az"
		}
		val url = urlBuilder().apply {
			addPathSegment("tim-kiem")
			addQueryParameter("sort", sortQuery)
			addQueryParameter("page", page.toString())
			if (!query.isNullOrEmpty()) {
				addQueryParameter("q", query)
			}
			if (!tags.isNullOrEmpty()) {
				val tagsQuery = tags.joinToString(separator = ",") { it.key }
				addEncodedQueryParameter("accept_genres", tagsQuery)
			}
		}.build()

		return webClient.httpGet(url).parseHtml()
			.select(".container .card.card-dark .row > .thumb-item-flow")
			.mapNotNull {
				val a = it.selectFirstOrThrow(".thumb-wrapper a")
				Manga(
					id = generateUid(a.attrAsRelativeUrl("href")),
					url = a.attrAsRelativeUrl("href"),
					publicUrl = a.attrAsAbsoluteUrl("href"),
					title = it.select(".thumb_attr.series-title").text(),
					altTitle = null,
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = a.selectFirst("div[data-bg]")?.attrAsAbsoluteUrl("data-bg").orEmpty(),
					tags = emptySet(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = chapter.url.toAbsoluteUrl(domain)
		return webClient.httpGet(url).parseHtml().select("#chapter-content > img").mapNotNull {
			val imageUrl = it.attrAsRelativeUrlOrNull("data-src")
				?: it.attrAsRelativeUrlOrNull("src")
				?: return@mapNotNull null
			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		return ArraySet(getOrCreateTagMap().values)
	}

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return it }
		val docs = webClient.httpGet("/tim-kiem".toAbsoluteUrl(domain)).parseHtml()
		val tags = docs.select(".search-border-left .row > .search-gerne_item").mapNotNull {
			MangaTag(
				title = it.text().trim(),
				key = it.selectFirst("label[data-genre-id]")
					?.attr("data-genre-id")
					?.trim() ?: return@mapNotNull null,
				source = source,
			)
		}
		val tagMap = tags.associateByTo(ArrayMap(tags.size)) { it.title }
		tagCache = tagMap
		return tagMap
	}
}
