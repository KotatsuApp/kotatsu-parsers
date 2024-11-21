package org.koitharu.kotatsu.parsers.site.mangareader

import androidx.collection.ArrayMap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MangaReaderParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int,
	searchPageSize: Int,
) : PagedMangaParser(context, source, pageSize, searchPageSize), Interceptor {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(
			SortOrder.UPDATED,
			SortOrder.POPULARITY,
			SortOrder.ALPHABETICAL,
			SortOrder.ALPHABETICAL_DESC,
			SortOrder.NEWEST,
		)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = getOrCreateTagMap().values.toSet(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.COMICS,
			ContentType.NOVEL,
		),
	)

	protected open val listUrl = "/manga"
	protected open val datePattern = "MMMM d, yyyy"
	protected open val isNetShieldProtected = false

	protected var tagCache: ArrayMap<String, MangaTag>? = null
	protected val mutex = Mutex()

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)

			when {

				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {
					append(listUrl)

					append("/?order=")
					append(
						when (order) {
							SortOrder.ALPHABETICAL -> "title"
							SortOrder.ALPHABETICAL_DESC -> "titlereverse"
							SortOrder.NEWEST -> "latest"
							SortOrder.POPULARITY -> "popular"
							SortOrder.UPDATED -> "update"
							else -> ""
						},
					)

					filter.tags.forEach {
						append("&")
						append("genre[]".urlEncoded())
						append("=")
						append(it.key)
					}

					filter.tagsExclude.forEach {
						append("&")
						append("genre[]".urlEncoded())
						append("=-")
						append(it.key)
					}

					if (filter.states.isNotEmpty()) {
						filter.states.oneOrThrowIfMany()?.let {
							append("&status=")
							when (it) {
								MangaState.ONGOING -> append("ongoing")
								MangaState.FINISHED -> append("completed")
								MangaState.PAUSED -> append("hiatus")
								else -> append("")
							}
						}
					}

					filter.types.oneOrThrowIfMany()?.let {
						append("&type=")
						append(
							when (it) {
								ContentType.MANGA -> "manga"
								ContentType.MANHWA -> "manhwa"
								ContentType.MANHUA -> "manhua"
								ContentType.COMICS -> "comic"
								ContentType.NOVEL -> "novel"
								else -> ""
							},
						)
					}

					append("&page=")
					append(page.toString())
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open val selectMangaList = ".postbody .listupd .bs .bsx"
	protected open val selectMangaListImg = "img.ts-post-image"
	protected open val selectMangaListTitle = "div.tt"

	protected open fun parseMangaList(docs: Document): List<Manga> {
		return docs.select(selectMangaList).mapNotNull {
			val a = it.selectFirst("a") ?: return@mapNotNull null
			val relativeUrl = a.attrAsRelativeUrl("href")
			val rating = it.selectFirst(".numscore")?.text()
				?.toFloatOrNull()?.div(10) ?: RATING_UNKNOWN
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				title = it.selectFirst(selectMangaListTitle)?.text() ?: a.attr("title"),
				altTitle = null,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				rating = rating,
				isNsfw = isNsfwSource,
				coverUrl = it.selectFirst(selectMangaListImg)?.src().orEmpty(),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	protected open val selectChapter = "#chapterlist > ul > li"
	override suspend fun getDetails(manga: Manga): Manga {
		val docs = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		val chapters = docs.select(selectChapter).mapChapters(reversed = true) { index, element ->
			val url = element.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapChapters null
			MangaChapter(
				id = generateUid(url),
				name = element.selectFirst(".chapternum")?.text() ?: "Chapter ${index + 1}",
				url = url,
				number = index + 1f,
				volume = 0,
				scanlator = null,
				uploadDate = dateFormat.tryParse(element.selectFirst(".chapterdate")?.text()),
				branch = null,
				source = source,
			)
		}
		return parseInfo(docs, manga, chapters)
	}

	protected open val detailsDescriptionSelector = "div.entry-content"

	open suspend fun parseInfo(docs: Document, manga: Manga, chapters: List<MangaChapter>): Manga {
		/// set if is table
		val tableMode =
			docs.selectFirst("div.seriestucontent > div.seriestucontentr") ?: docs.selectFirst("div.seriestucontentr")
			?: docs.selectFirst("div.seriestucon")

		val tagMap = getOrCreateTagMap()

		val selectTag = if (tableMode != null) {
			docs.select(".seriestugenre > a")
		} else {
			docs.select(".wd-full .mgen > a")
		}

		val tags = selectTag.mapNotNullToSet { tagMap[it.text()] }

		val stateSelect = if (tableMode != null) {
			tableMode.selectFirst(".infotable td:contains(Status)")
				?: tableMode.selectFirst(".infotable td:contains(Statut)")
				?: tableMode.selectFirst(".infotable td:contains(حالة العمل)")
				?: tableMode.selectFirst(".infotable td:contains(الحالة)")
				?: tableMode.selectFirst(".infotable td:contains(Estado)")
				?: tableMode.selectFirst(".infotable td:contains(สถานะ)")
				?: tableMode.selectFirst(".infotable td:contains(Stato )")
				?: tableMode.selectFirst(".infotable td:contains(Durum)")
				?: tableMode.selectFirst(".infotable td:contains(Statüsü)")

		} else {
			docs.selectFirst(".tsinfo div:contains(Status)") ?: docs.selectFirst(".tsinfo div:contains(Statut)")
			?: docs.selectFirst(".tsinfo div:contains(حالة العمل)") ?: docs.selectFirst(".tsinfo div:contains(Estado)")
			?: docs.selectFirst(".tsinfo div:contains(สถานะ)") ?: docs.selectFirst(".tsinfo div:contains(Stato )")
			?: docs.selectFirst(".tsinfo div:contains(Durum)") ?: docs.selectFirst(".tsinfo div:contains(Statüsü)")
			?: docs.selectFirst(".tsinfo div:contains(Statü)") ?: docs.selectFirst(".tsinfo div:contains(الحالة)")
		}

		val state = if (tableMode != null) {
			stateSelect?.lastElementSibling()

		} else {
			stateSelect?.lastElementChild()
		}

		val mangaState = state?.let {
			when (it.text()) {

				"مستمرة", "En curso", "En Curso", "Ongoing", "OnGoing", "On going", "Ativo", "En Cours", "En cours", "En cours \uD83D\uDFE2",
				"En cours de publication", "Đang tiến hành", "Em lançamento", "em lançamento", "Em Lançamento", "Онгоінг", "Publishing",
				"Devam Ediyor", "Em Andamento", "In Corso", "Güncel", "Berjalan", "Продолжается", "Updating", "Lançando", "In Arrivo", "Emision",
				"En emision", "مستمر", "Curso", "En marcha", "Publicandose", "Publicando", "连载中", "Devam ediyor", "Devam Etmekte",
					-> MangaState.ONGOING

				"Completed", "Completo", "Complété", "Fini", "Achevé", "Terminé", "Terminé ⚫", "Tamamlandı", "Đã hoàn thành", "Hoàn Thành",
				"مكتملة", "Завершено", "Finished", "Finalizado", "Completata", "One-Shot", "Bitti", "Tamat", "Completado", "Concluído",
				"Concluido", "已完结", "Bitmiş",
					-> MangaState.FINISHED

				"Canceled", "Cancelled", "Cancelado", "cancellato", "Cancelados", "Dropped", "Discontinued", "abandonné", "Abandonné",
					-> MangaState.ABANDONED

				"Hiatus", "On Hold", "Pausado", "En espera", "En pause", "En Pause", "En attente",
					-> MangaState.PAUSED

				else -> null
			}
		}

		val author = tableMode?.selectFirst(".infotable td:contains(Author)")?.lastElementSibling()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Author)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Auteur)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Artist)")?.lastElementChild()?.text()
			?: docs.selectFirst(".tsinfo div:contains(Durum)")?.lastElementChild()?.text()

		val nsfw = docs.selectFirst(".restrictcontainer") != null
			|| docs.selectFirst(".info-right .alr") != null
			|| docs.selectFirst(".postbody .alr") != null

		return manga.copy(
			description = docs.selectFirst(detailsDescriptionSelector)?.text(),
			state = mangaState,
			author = author,
			isNsfw = manga.isNsfw || nsfw,
			tags = tags,
			chapters = chapters,
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return parseMangaList(webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml())
	}

	protected open val encodedSrc = false
	protected open val selectScript = "div.wrapper script"
	protected open val selectPage = "div#readerarea img"
	protected open val selectTestScript = "script:containsData(ts_reader)"
	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterUrl = chapter.url.toAbsoluteUrl(domain)
		val docs = webClient.httpGet(chapterUrl).parseHtml()

		val test = docs.select(selectTestScript)
		if (test.isNullOrEmpty() and !encodedSrc) {
			return docs.select(selectPage).map { img ->
				val url = img.requireSrc().toRelativeUrl(domain)
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
			val images = if (encodedSrc) {
				val script = docs.select(selectScript)
				var decode = ""
				for (i in script) {
					if (i.attr("src").startsWith("data:text/javascript;base64,")) {
						decode = Base64.getDecoder().decode(i.attr("src").replace("data:text/javascript;base64,", ""))
							.decodeToString()
						if (decode.startsWith("ts_reader.run")) {
							break
						}
					}
				}
				JSONObject(decode.substringAfter('(').substringBeforeLast(')'))
					.getJSONArray("sources")
					.getJSONObject(0)
					.getJSONArray("images")

			} else {
				val script = docs.selectFirstOrThrow(selectTestScript)
				JSONObject(script.data().substringAfter('(').substringBeforeLast(')'))
					.getJSONArray("sources")
					.getJSONObject(0)
					.getJSONArray("images")
			}

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

	protected open suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
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

	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (!isNetShieldProtected) {
			return response
		}
		val contentType = response.mimeType
		if (
			contentType?.endsWith("/html") != false &&
			context.cookieJar.getCookies(domain).none { it.name.contains("NetShield") }
		) {
			val cookie = runBlocking { response.copy().parseHtml().getNetShieldCookie() } ?: return response
			context.cookieJar.insertCookie(domain, cookie)
			return chain.proceed(response.request.newBuilder().build()).also {
				response.closeQuietly()
			}
		}
		return response
	}

	private suspend fun Document.getNetShieldCookie(): Cookie? = runCatchingCancellable {
		val script = select("script").firstNotNullOfOrNull { s ->
			s.html().takeIf { x -> x.contains("slowAES.decrypt") }
		} ?: return@runCatchingCancellable null
		val min = webClient.httpGet("https://$domain/min.js").parseRaw()
		val res = context.evaluateJs(min + "\n\n" + script.replace(Regex("document.cookie\\s*=\\s*"), "return "))
		res?.let {
			Cookie.parse(baseUri().toHttpUrl(), it)
		}
	}.getOrNull()
}
