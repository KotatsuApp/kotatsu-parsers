package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.WordSet
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toMutableMap
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumSet


internal abstract class MadaraParser(
	context: MangaLoaderContext,
	source: MangaSource,
	domain: String,
	pageSize: Int = 12,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	protected open val tagPrefix = "manga-genre/"
	protected open val isNsfwSource = false
	protected open val datePattern = "MMMM dd, yyyy"

	protected open val postreq = false

	init {
		paginator.firstPage = 0
		searchPaginator.firstPage = 0
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


	protected val ongoing: Array<String> = arrayOf(
		"مستمرة",
		"En curso",
		"En Curso",
		"Ongoing",
		"OnGoing",
		"On going",
		"Ativo",
		"En Cours",
		"En cours",
		"En cours \uD83D\uDFE2",
		"En cours de publication",
		"Đang tiến hành",
		"Em lançamento",
		"em lançamento",
		"Em Lançamento",
		"Онгоінг",
		"Publishing",
		"Devam Ediyor",
		"Em Andamento",
		"In Corso",
		"Güncel",
		"Berjalan",
		"Продолжается",
		"Updating",
		"Lançando",
		"In Arrivo",
		"Emision",
		"En emision",
		"مستمر",
		"Curso",
		"En marcha",
		"Publicandose",
		"Publicando",
		"连载中",
		"Devam ediyor",
	)

	protected val finished: Array<String> = arrayOf(
		"Completed",
		"Completo",
		"Complété",
		"Fini",
		"Achevé",
		"Terminé",
		"Terminé ⚫",
		"Tamamlandı",
		"Đã hoàn thành",
		"Hoàn Thành",
		"مكتملة",
		"Завершено",
		"Finished",
		"Finalizado",
		"Completata",
		"One-Shot",
		"Bitti",
		"Tamat",
		"Completado",
		"Concluído",
		"Concluido",
		"已完结",
		"Bitmiş",
	)


	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val tag = tags.oneOrThrowIfMany()
		val payload = createRequestTemplate()
		payload["page"] = page.toString()
		payload["vars[meta_key]"] = when (sortOrder) {
			SortOrder.POPULARITY -> "_wp_manga_views"
			SortOrder.UPDATED -> "_latest_update"
			else -> "_wp_manga_views"
		}
		payload["vars[wp-manga-genre]"] = tag?.key.orEmpty()
		payload["vars[s]"] = query?.urlEncoded().orEmpty()
		val doc = webClient.httpPost(
			"https://$domain/wp-admin/admin-ajax.php",
			payload,
		).parseHtml()
		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail.manga")
		}.map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()?.trim()
					?.lowercase()) {
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
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/manga/").parseHtml()
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
			val href = a.attr("href").removeSuffix("/").substringAfterLast(tagPrefix, "")
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

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val testchekasync = doc.body().select("div.listing-chapters_wrap")

		val chaptersDeferred = if (testchekasync.isNullOrEmpty()) {
			async { loadChapters(manga.url, doc) }
		} else {
			async { getChapters(manga, doc) }
		}

		val desc = doc.body().selectFirst("div.description-summary div.summary__content") ?: doc.body()
			.selectFirst("div.summary_content div.post-content_item > h5 + div") ?: doc.body()
			.selectFirst("div.post-content div.manga-summary") ?: doc.body()
			.selectFirst("div.post-content div.desc") ?: doc.body()
			.selectFirst("div.summary_content div.manga-excerpt")


		val stateselect =
			doc.body().select("div.post-content_item:contains(Status) > div.summary-content").last() ?: doc.body()
				.select("div.post-content_item:contains(Statut) > div.summary-content").last()
			?: doc.body().select("div.post-content_item:contains(حالة العمل) > div.summary-content").last()
			?: doc.body().select("div.post-content_item:contains(Estado) > div.summary-content").last()
			?: doc.body().select("div.post-content_item:contains(สถานะ) > div.summary-content").last() ?: doc.body()
				.select("div.post-content_item:contains(Stato) > div.summary-content").last()
			?: doc.body().select("div.post-content_item:contains(Durum) > div.summary-content").last() ?: doc.body()
				.select("div.post-content_item:contains(Statüsü) > div.summary-content").last()
			?: doc.body().select("div.summary-content").last()

		val state =
			stateselect?.let {
				when (it.text()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					else -> null
				}
			}

		manga.copy(
			tags = doc.body().select("div.genres-content a").mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc?.select("p")?.filterNot { it.ownText().startsWith("A brief description") }
				?.joinToString { it.text() },
			altTitle =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()?.text()
				?.trim() ?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
				.firstOrNull()?.tableValue()?.text()?.trim(),
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	protected open suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val root2 = doc.body().selectFirstOrThrow("div.content-area")
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return root2.select("li.wp-manga-chapter").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + "?style=list"
			MangaChapter(
				id = generateUid(href),
				name = a.ownText(),
				number = i + 1,
				url = link,
				uploadDate = parseChapterDate(
					dateFormat,
					li.selectFirst("span.chapter-release-date i")?.text(),
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	protected open suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {

		val doc = if (postreq == false) {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
			webClient.httpPost(url, emptyMap()).parseHtml()
		} else {
			val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
			val url = "https://$domain/wp-admin/admin-ajax.php"
			val postdata = "action=manga_get_chapters&manga=$mangaId"

			webClient.httpPost(url, postdata).parseHtml()
		}
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.select("li.wp-manga-chapter").mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + "?style=list"
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = a.text(),
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					li.selectFirst("span.chapter-release-date i")?.text(),
				),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirst("div.main-col-inner")?.selectFirst("div.reading-content")
			?: throw ParseException("Root not found", fullUrl)
		return root.select("div.page-break").map { div ->
			val img = div.selectFirst("img") ?: div.parseFailed("Page image not found")
			val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		date ?: return 0
		return when {
			date.endsWith(" ago", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in Portuguese.
			date.endsWith(" atrás", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in Turkish.
			date.endsWith(" önce", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in Viêt Nam.
			date.endsWith(" trước", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated 'ago' in french.
			date.startsWith("il y a", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle translated short 'ago'
			date.endsWith(" h", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			date.endsWith(" d", ignoreCase = true) -> {
				parseRelativeDate(date)
			}

			date.endsWith(" mins", ignoreCase = true) -> {
				parseRelativeDate(date)
			}
			// Handle 'yesterday' and 'today', using midnight
			date.startsWith("year", ignoreCase = true) -> {
				Calendar.getInstance().apply {
					add(Calendar.DAY_OF_MONTH, -1) // yesterday
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

			date.startsWith("today", ignoreCase = true) -> {
				Calendar.getInstance().apply {
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
				// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
				date.split(" ").map {
					if (it.contains(Regex("""\d\D\D"""))) {
						it.replace(Regex("""\D"""), "")
					} else {
						it
					}
				}.let { dateFormat.tryParse(it.joinToString(" ")) }
			}

			else -> dateFormat.tryParse(date)
		}
	}

	// Parses dates in this form:
	// 21 hours ago
	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()

		return when {
			WordSet(
				"hari",
				"gün",
				"jour",
				"día",
				"dia",
				"day",
				"d",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "hour", "h").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet("menit", "dakika", "min", "minute", "minuto", "mins", "phút").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("detik", "segundo", "second").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	protected fun Element.src(): String? {
		var result = absUrl("data-src")
		if (result.isEmpty()) result = absUrl("data-cfsrc")
		if (result.isEmpty()) result = absUrl("src")
		return result.ifEmpty { null }
	}

	private fun createRequestTemplate() =
		("action=madara_load_more&page=1&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5B" + "orderby%5D=meta_value_num&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query" + "%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5Brelation%5D=OR&vars%5Bpost_type" + "%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmeta_key%5D=_latest_update&vars%5Border" + "%5D=desc&vars%5Bmanga_archives_item_layout%5D=default").split(
			'&',
		).map {
			val pos = it.indexOf('=')
			it.substring(0, pos) to it.substring(pos + 1)
		}.toMutableMap()

}
