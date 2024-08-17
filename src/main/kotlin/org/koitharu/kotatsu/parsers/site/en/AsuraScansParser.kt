package org.koitharu.kotatsu.parsers.site.en

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.toJSONList
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("ASURASCANS", "AsuraComic", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.ASURASCANS, pageSize = 30) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.RATING,
		SortOrder.UPDATED,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.ALPHABETICAL,
	)

	override val availableStates: Set<MangaState> = EnumSet.allOf(MangaState::class.java)

	override val configKeyDomain = ConfigKey.Domain("asuracomic.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val isMultipleTagsSupported = true

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/series?page=")
			append(page)

			when (filter) {
				is MangaListFilter.Search -> {
					append("&name=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						append("&genres=")
						append(filter.tags.joinToString(separator = ",") { it.key })
					}

					filter.states.oneOrThrowIfMany()?.let {
						append("&status=")
						append(
							when (it) {
								MangaState.ONGOING -> "1"
								MangaState.FINISHED -> "3"
								MangaState.ABANDONED -> "4"
								MangaState.PAUSED -> "2"
								MangaState.UPCOMING -> "6"
							},
						)
					}

					append("&types=-1&order=")
					when (filter.sortOrder) {
						SortOrder.RATING -> append("rating")
						SortOrder.UPDATED -> append("update")
						SortOrder.NEWEST -> append("latest")
						SortOrder.ALPHABETICAL_DESC -> append("desc")
						SortOrder.ALPHABETICAL -> append("asc")
						else -> append("update")
					}
				}

				null -> append("&genres=&status=-1&order=update&types=-1")
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.grid > a[href]").map { a ->
			val href = "/" + a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = a.selectFirst("img")?.src().orEmpty(),
				title = a.selectFirst("div.block > span.block")?.text().orEmpty(),
				altTitle = null,
				rating = a.selectFirst("div.block  label.ml-1")?.text()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = when (a.selectLastOrThrow("span.status").text()) {
					"Ongoing" -> MangaState.ONGOING
					"Completed" -> MangaState.FINISHED
					"Hiatus" -> MangaState.PAUSED
					"Dropped" -> MangaState.ABANDONED
					"Coming Soon" -> MangaState.UPCOMING
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val json =
			webClient.httpGet("https://gg.$domain/api/series/filters").parseJson().getJSONArray("genres").toJSONList()
		for (el in json) {
			if (el.getString("name").isEmpty()) continue
			tagMap[el.getString("name")] = MangaTag(
				key = el.getInt("id").toString(),
				title = el.getString("name"),
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	private val regexDate = """(\d+)(st|nd|rd|th)""".toRegex()

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val tagMap = getOrCreateTagMap()
		val selectTag = doc.select("div[class^=space] > div.flex > button.text-white")
		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			description = doc.selectFirst("span.font-medium.text-sm")?.text().orEmpty(),
			tags = tags,
			author = doc.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Author)) > h3:eq(1)")?.text(),
			chapters = doc.select("div.scrollbar-thumb-themecolor > div.group").mapChapters(reversed = true) { i, div ->
				val a = div.selectLastOrThrow("a")
				val urlRelative = "/series/" + a.attrAsRelativeUrl("href")
				val url = urlRelative.toAbsoluteUrl(domain)
				val date = div.selectFirst("h3:eq(1)")!!.ownText()
				val cleanDate = date.replace(regexDate, "$1")
				MangaChapter(
					id = generateUid(url),
					name = div.selectFirstOrThrow("h3:eq(0)").text(),
					number = i + 1f,
					volume = 0,
					url = url,
					scanlator = null,
					uploadDate = SimpleDateFormat("MMMM d yyyy", Locale.US)
						.tryParse(cleanDate),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div > img[alt*=chapter]").map { img ->
			val urlPage = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(urlPage),
				url = urlPage,
				preview = null,
				source = source,
			)
		}
	}
}
