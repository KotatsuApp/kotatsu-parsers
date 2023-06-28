package org.koitharu.kotatsu.parsers.site.mangareader

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*


internal abstract class MangaReaderParser(
	context: MangaLoaderContext,
	source: MangaSource,
	pageSize: Int,
	searchPageSize: Int,
) : PagedMangaParser(context, source, pageSize, searchPageSize) {

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	protected val idLocale
		get() = Locale("in", "ID")

	abstract val listUrl: String
	abstract val tableMode: Boolean
	protected open val isNsfwSource = false
	open val chapterDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()
	private var lastSearchPage = 1

	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val chapters = docs.select("#chapterlist > ul > li").mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1,
				scanlator = null,
				uploadDate = chapterDateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source,
			)
		}
		return if (tableMode) parseInfoTable(docs, manga, chapters) else parseInfoList(docs, manga, chapters)
	}

	open suspend fun parseInfoTable(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		val mangaInfo =
			docs.selectFirst("div.seriestucontent > div.seriestucontentr") ?: docs.selectFirst("div.seriestucontentr")
			?: docs.selectFirst("div.seriestucon")

		val state_select =
			docs.selectFirst(".tsinfo div:contains(Status)") ?: docs.selectFirst(".tsinfo div:contains(Statut)")
			?: docs.selectFirst(".tsinfo div:contains(حالة العمل)") ?: docs.selectFirst(".tsinfo div:contains(Estado)")
			?: docs.selectFirst(".tsinfo div:contains(สถานะ)") ?: docs.selectFirst(".tsinfo div:contains(Stato )")
			?: docs.selectFirst(".tsinfo div:contains(Durum)")

		val mangaState = state_select?.lastElementChild()?.let {
			when (it.text()) {
				"مستمرةا",
				"En curso",
				"Ongoing",
				"On going",
				"Ativo",
				"En Cours",
				"OnGoing",
				"Đang tiến hành",
				"em lançamento",
				"Онгоінг",
				"Publishing",
				"Devam Ediyor",
				"Em Andamento",
				"In Corso",
				-> MangaState.ONGOING

				"Completed",
				"Completo",
				"Complété",
				"Fini",
				"Terminé",
				"Tamamlandı",
				"Đã hoàn thành",
				"مكتملة",
				"Завершено",
				"Finished",
				"Finalizado",
				"Completata",
				-> MangaState.FINISHED

				else -> null
			}
		}
		val tagMap = getOrCreateTagMap()
		val tags = mangaInfo?.select(".seriestugenre > a")?.mapNotNullToSet { tagMap[it.text()] }

		return manga.copy(
			description = mangaInfo?.selectFirst("div.entry-content")?.html(),
			state = mangaState,
			author = mangaInfo?.selectFirst(".infotable td:contains(Author)")?.lastElementSibling()?.text(),
			isNsfw = manga.isNsfw || docs.selectFirst(".restrictcontainer") != null,
			tags = tags.orEmpty(),
			chapters = chapters,
		)
	}

	open suspend fun parseInfoList(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		val state_select =
			docs.selectFirst(".tsinfo div:contains(Status)") ?: docs.selectFirst(".tsinfo div:contains(Statut)")
			?: docs.selectFirst(".tsinfo div:contains(حالة العمل)") ?: docs.selectFirst(".tsinfo div:contains(Estado)")
			?: docs.selectFirst(".tsinfo div:contains(สถานะ)") ?: docs.selectFirst(".tsinfo div:contains(Stato )")
			?: docs.selectFirst(".tsinfo div:contains(Durum)")

		val mangaState = state_select?.lastElementChild()?.let {
			when (it.text()) {
				"مستمرةا",
				"En curso",
				"Ongoing",
				"On going",
				"Ativo",
				"En Cours",
				"OnGoing",
				"Đang tiến hành",
				"em lançamento",
				"Онгоінг",
				"Publishing",
				"Devam Ediyor",
				"Em Andamento",
				"In Corso",
				-> MangaState.ONGOING

				"Completed",
				"Completo",
				"Complété",
				"Fini",
				"Terminé",
				"Tamamlandı",
				"Đã hoàn thành",
				"مكتملة",
				"Завершено",
				"Finished",
				"Finalizado",
				"Completata",
				-> MangaState.FINISHED

				else -> null
			}
		}

		val tags = docs.select(".wd-full .mgen > a").mapNotNullToSet { getOrCreateTagMap()[it.text()] }

		return manga.copy(
			description =
			docs.selectFirst("div.entry-content")?.html(),

			state = mangaState,
			author =
			docs.selectFirst(".tsinfo div:contains(Author)")?.lastElementChild()?.text()
				?: docs.selectFirst(".tsinfo div:contains(Auteur)")?.lastElementChild()?.text()
				?: docs.selectFirst(".tsinfo div:contains(Artist)")?.lastElementChild()?.text()
				?: docs.selectFirst(".tsinfo div:contains(Durum)")?.lastElementChild()?.text(),

			isNsfw = manga.isNsfw
				|| docs.selectFirst(".info-right .alr") != null
				|| docs.selectFirst(".postbody .alr") != null,
			tags = tags,
			chapters = chapters,
		)
	}


	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			if (page > lastSearchPage) {
				return emptyList()
			}

			val url = buildString {
				append("https://")
				append(domain)
				append("/page/")
				append(page)
				append("/?s=")
				append(query.urlEncoded())
			}

			val docs = webClient.httpGet(url).parseHtml()
			lastSearchPage = docs.selectFirst(".pagination .next")
				?.previousElementSibling()
				?.text()?.toIntOrNull() ?: 1
			return parseMangaList(docs)
		}

		val sortQuery = when (sortOrder) {
			SortOrder.ALPHABETICAL -> "title"
			SortOrder.NEWEST -> "latest"
			SortOrder.POPULARITY -> "popular"
			SortOrder.UPDATED -> "update"
			else -> ""
		}
		val tagKey = "genre[]".urlEncoded()
		val tagQuery =
			if (tags.isNullOrEmpty()) "" else tags.joinToString(separator = "&", prefix = "&") { "$tagKey=${it.key}" }
		val url = buildString {
			append("https://")
			append(domain)
			append(listUrl)
			append("/?order=")
			append(sortQuery)
			append(tagQuery)
			append("&page=")
			append(page)
		}

		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	private fun parseMangaList(docs: Document): List<Manga> {
		return docs.select(".postbody .listupd .bs .bsx").mapNotNull {
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN

			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = a.attr("title"),
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirst("img.ts-post-image")?.imageUrl().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()
		val script = docs.selectFirstOrThrow("script:containsData(ts_reader)")
		val images = JSONObject(script.data().substringAfter('(').substringBeforeLast(')'))
			.getJSONArray("sources")
			.getJSONObject(0)
			.getJSONArray("images")

		val pages = ArrayList<MangaPage>(images.length())
		for (i in 0 until images.length()) {
			pages.add(
				MangaPage(
					id = generateUid(images.getString(i)),
					url = images.getString(i),
					preview = null,
					source = source,
				),
			)
		}

		return pages
	}

	override suspend fun getTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	protected suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()

		val url = listUrl.toAbsoluteUrl(domain)
		val tagElements = webClient.httpGet(url).parseHtml().select("ul.genrez > li")
		for (el in tagElements) {
			if (el.text().isEmpty()) continue

			tagMap[el.text()] = MangaTag(
				title = el.text(),
				key = el.selectFirst("input")?.attr("value") ?: continue,
				source = source,
			)
		}

		tagCache = tagMap
		return@withLock tagMap
	}

	private fun Element.imageUrl(): String {
		return attrAsAbsoluteUrlOrNull("src")
			?: attrAsAbsoluteUrlOrNull("data-src")
			?: attrAsAbsoluteUrlOrNull("data-cfsrc")
			?: ""
	}
}
