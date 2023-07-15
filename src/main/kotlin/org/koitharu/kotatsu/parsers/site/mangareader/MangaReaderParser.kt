package org.koitharu.kotatsu.parsers.site.mangareader

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrl
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale


internal abstract class MangaReaderParser(
	context: MangaLoaderContext,
	source: MangaSource,
	pageSize: Int,
	searchPageSize: Int,
) : PagedMangaParser(context, source, pageSize, searchPageSize) {

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.ALPHABETICAL, SortOrder.NEWEST)

	protected open val listUrl = "/manga"
	protected open val isNsfwSource = false
	open val chapterDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

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
		return parseInfo(docs, manga, chapters)
	}

	open suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {

		/// set if is table
		val tablemode =
			docs.selectFirst("div.seriestucontent > div.seriestucontentr") ?: docs.selectFirst("div.seriestucontentr")
			?: docs.selectFirst("div.seriestucon")


		val tagMap = getOrCreateTagMap()

		val selectTag = if (tablemode != null) {
			docs.select(".seriestugenre > a")
		} else {
			docs.select(".wd-full .mgen > a")
		}

		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }


		val stateSelect = if (tablemode != null) {
			tablemode.selectFirst(".infotable td:contains(Status)")
				?: tablemode.selectFirst(".infotable td:contains(Statut)")
				?: tablemode.selectFirst(".infotable td:contains(حالة العمل)")
				?: tablemode.selectFirst(".infotable td:contains(Estado)")
				?: docs.selectFirst(".infotable td:contains(สถานะ)")
				?: tablemode.selectFirst(".infotable td:contains(Stato )")
				?: tablemode.selectFirst(".infotable td:contains(Durum)")
				?: tablemode.selectFirst(".infotable td:contains(Statüsü)")

		} else {
			docs.selectFirst(".tsinfo div:contains(Status)") ?: docs.selectFirst(".tsinfo div:contains(Statut)")
			?: docs.selectFirst(".tsinfo div:contains(حالة العمل)") ?: docs.selectFirst(".tsinfo div:contains(Estado)")
			?: docs.selectFirst(".tsinfo div:contains(สถานะ)") ?: docs.selectFirst(".tsinfo div:contains(Stato )")
			?: docs.selectFirst(".tsinfo div:contains(Durum)") ?: docs.selectFirst(".tsinfo div:contains(Statüsü)")
		}

		val state = if (tablemode != null) {
			stateSelect?.lastElementSibling()

		} else {
			stateSelect?.lastElementChild()
		}


		val mangaState = state?.let {
			when (it.text()) {
				"مستمرة", "En curso", "En Curso", "Ongoing", "OnGoing", "On going", "Ativo", "En Cours", "En cours",
				"En cours \uD83D\uDFE2", "En cours de publication", "Đang tiến hành", "Em lançamento", "em lançamento", "Em Lançamento",
				"Онгоінг", "Publishing", "Devam Ediyor", "Em Andamento", "In Corso", "Güncel", "Berjalan", "Продолжается", "Updating",
				"Lançando", "In Arrivo", "Emision", "En emision", "مستمر", "Curso", "En marcha", "Publicandose", "Publicando", "连载中",
				"Devam ediyor",
				-> MangaState.ONGOING

				"Completed", "Completo", "Complété", "Fini", "Achevé", "Terminé", "Terminé ⚫", "Tamamlandı", "Đã hoàn thành", "Hoàn Thành", "مكتملة",
				"Завершено", "Finished", "Finalizado", "Completata", "One-Shot", "Bitti", "Tamat", "Completado", "Concluído", "Concluido", "已完结", "Bitmiş",
				-> MangaState.FINISHED

				else -> null
			}
		}


		val author = tablemode?.selectFirst(".infotable td:contains(Author)")?.lastElementSibling()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Author)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Auteur)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Artist)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Durum)")?.lastElementChild()?.text()

		val nsfw = docs.selectFirst(".restrictcontainer") != null
				|| docs.selectFirst(".info-right .alr") != null
				|| docs.selectFirst(".postbody .alr") != null

		return manga.copy(
			description = docs.selectFirst("div.entry-content")?.text(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
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

		val test = docs.select("script:containsData(ts_reader)")
		if (test.isNullOrEmpty()) {
			return docs.select("div#readerarea img").map { img ->
				val url = img.imageUrl()
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
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
