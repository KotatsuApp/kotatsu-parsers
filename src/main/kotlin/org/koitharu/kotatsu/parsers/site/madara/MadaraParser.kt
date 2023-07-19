package org.koitharu.kotatsu.parsers.site.madara

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

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
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
	)

	protected open val tagPrefix = "manga-genre/"
	protected open val isNsfwSource = false
	protected open val datePattern = "MMMM d, yyyy"
	protected open val stylepage = "?style=list"

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


	@JvmField
	protected val ongoing: Set<String> = hashSetOf(
		"مستمرة",
		"En curso",
		"En Curso",
		"Ongoing",
		"OnGoing",
		"On going",
		"On Going",
		"Ativo",
		"En Cours",
		"En cours",
		"En cours \uD83D\uDFE2",
		"En cours de publication",
		"Activo",
		"Đang tiến hành",
		"Em lançamento",
		"em lançamento",
		"Em Lançamento",
		"Онгоінг",
		"Publishing",
		"Devam Ediyor",
		"Em Andamento",
		"Em andamento",
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

	@JvmField
	protected val finished: Set<String> = hashSetOf(
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
		"End",
	)

	// Change these values only if the site does not support manga listings via ajax
	protected open val withoutAjax = false
	protected open val listeurl = "manga/"

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {

		val doc = if (withoutAjax) {
			val url = buildString {
				append("https://")
				append(domain)
				val pages = page + 1

				when {
					!query.isNullOrEmpty() -> {
						append("/page/")
						append(pages.toString())
						append("/?s=")
						append(query.urlEncoded())
						append("&post_type=wp-manga&")
					}

					!tags.isNullOrEmpty() -> {
						append("/$tagPrefix")
						for (tag in tags) {
							append(tag.key)
						}
						append("/page/")
						append(pages.toString())
						append("?")
					}

					else -> {

						append("/$listeurl")
						append("/page/")
						append(pages.toString())
						append("?")
					}
				}
				append("m_orderby=")
				when (sortOrder) {
					SortOrder.POPULARITY -> append("views")
					SortOrder.UPDATED -> append("latest")
					SortOrder.NEWEST -> append("new-manga")
					SortOrder.ALPHABETICAL -> append("alphabet")
					else -> append("latest")
				}
			}
			webClient.httpGet(url).parseHtml()
		} else {
			val tag = tags.oneOrThrowIfMany()
			val payload = createRequestTemplate()
			payload["page"] = page.toString()
			when (sortOrder) {
				SortOrder.POPULARITY -> payload["vars[meta_key]"] = "_wp_manga_views"
				SortOrder.UPDATED -> payload["vars[meta_key]"] = "_latest_update"
				SortOrder.NEWEST -> payload["vars[meta_key]"] = ""
				SortOrder.ALPHABETICAL -> {
					payload["vars[orderby]"] = "post_title"
					payload["vars[order]"] = "ASC"
				}

				else -> payload["vars[meta_key]"] = "_latest_update"
			}
			payload["vars[wp-manga-genre]"] = tag?.key.orEmpty()
			payload["vars[s]"] = query?.urlEncoded().orEmpty()
			webClient.httpPost(
				"https://$domain/wp-admin/admin-ajax.php",
				payload,
			).parseHtml()
		}

		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail")
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
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()
					?.lowercase()) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
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

	protected open val selectdesc =
		"div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt, div.post-content div.manga-summary, div.post-content div.desc, div.c-page__content div.summary__content"
	protected open val selectgenre = "div.genres-content a"
	protected open val selectdate = "span.chapter-release-date i"
	protected open val selectchapter = "li.wp-manga-chapter"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()

		val testchekasync = body.select("div.listing-chapters_wrap")

		val chaptersDeferred = if (testchekasync.isNullOrEmpty()) {
			async { loadChapters(manga.url, doc) }
		} else {
			async { getChapters(manga, doc) }
		}

		val desc = body.select(selectdesc).let {
			if (it.select("p").text().isNotEmpty()) {
				it.select("p").joinToString(separator = "\n\n") { p ->
					p.text().replace("<br>", "\n")
				}
			} else {
				it.text()
			}
		}

		val stateDiv = (body.selectFirst("div.post-content_item:contains(Status)")
			?: body.selectFirst("div.post-content_item:contains(Statut)")
			?: body.selectFirst("div.post-content_item:contains(État)")
			?: body.selectFirst("div.post-content_item:contains(حالة العمل)")
			?: body.selectFirst("div.post-content_item:contains(Estado)")
			?: body.selectFirst("div.post-content_item:contains(สถานะ)")
			?: body.selectFirst("div.post-content_item:contains(Stato)")
			?: body.selectFirst("div.post-content_item:contains(Durum)")
			?: body.selectFirst("div.post-content_item:contains(Statüsü)")
			?: body.selectFirst("div.post-content_item:contains(Статус)")
			?: body.selectFirst("div.post-content_item:contains(状态)")
			?: body.selectFirst("div.post-content_item:contains(الحالة)"))?.selectLast("div.summary-content")

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()?.text()
				?.trim() ?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
				.firstOrNull()?.tableValue()?.text()?.trim()

		manga.copy(
			tags = doc.body().select(selectgenre).mapNotNullToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix("/").substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			state = state,
			chapters = chaptersDeferred.await(),
		)
	}

	protected open suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val root2 = doc.body().selectFirstOrThrow("div.content-area")
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return root2.select(selectchapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylepage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectdate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1,
				url = link,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				source = source,
				scanlator = null,
				branch = null,
			)
		}
	}

	protected open suspend fun loadChapters(mangaUrl: String, document: Document): List<MangaChapter> {

		val doc = if (postreq) {
			val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
			val url = "https://$domain/wp-admin/admin-ajax.php"
			val postdata = "action=manga_get_chapters&manga=$mangaId"
			webClient.httpPost(url, postdata).parseHtml()
		} else {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
			webClient.httpPost(url, emptyMap()).parseHtml()
		}
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		return doc.select(selectchapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylepage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectdate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = i + 1,
				branch = null,
				uploadDate = parseChapterDate(
					dateFormat,
					dateText,
				),
				scanlator = null,
				source = source,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val chapterProtector = doc.getElementById("chapter-protector-data")
		if (chapterProtector == null) {
			val root = doc.body().selectFirstOrThrow("div.main-col-inner").selectFirstOrThrow("div.reading-content")
			return root.select("div.page-break").map { div ->
				val img = div.selectFirstOrThrow("img")
				val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}
		} else {
			val chapterProtectorHtml = chapterProtector.html()
			val password = chapterProtectorHtml.substringAfter("wpmangaprotectornonce='").substringBefore("';")
			val chapterData = JSONObject(
				chapterProtectorHtml.substringAfter("chapter_data='").substringBefore("';").replace("\\/", "/"),
			)
			val unsaltedCiphertext = context.decodeBase64(chapterData.getString("ct"))
			val salt = chapterData.getString("s").toString().decodeHex()
			val ciphertext = SALTED + salt + unsaltedCiphertext

			val rawImgArray = CryptoAES(context).decrypt(context.encodeBase64(ciphertext), password)
			val imgArrayString = rawImgArray.filterNot { c -> c == '[' || c == ']' || c == '\\' || c == '"' }

			return imgArrayString.split(",").map { url ->
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				)
			}

		}
	}

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		// Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
		val d = date?.lowercase() ?: return 0
		return when {
			d.endsWith(" ago") || d.endsWith(" atrás") || // Handle translated 'ago' in Portuguese.
				d.startsWith("há ") || // other translated 'ago' in Portuguese.
				d.endsWith(" hace") || // other translated 'ago' in Spanish
				d.endsWith(" önce") || // Handle translated 'ago' in Turkish.
				d.endsWith(" trước") || // Handle translated 'ago' in Viêt Nam.
				d.startsWith("il y a") || // Handle translated 'ago' in French.
				//If there is no ago but just a motion of time
				// short Hours
				d.endsWith(" h") ||
				// short Day
				d.endsWith(" d") ||
				// Day in Portuguese
				d.endsWith(" días") || d.endsWith(" día") ||
				// Day in French
				d.endsWith(" jour") || d.endsWith(" jours") ||
				// Hours in Portuguese
				d.endsWith(" horas") || d.endsWith(" hora") ||
				// Hours in french
				d.endsWith(" heure") || d.endsWith(" heures") ||
				// Minutes in English
				d.endsWith(" mins") ||
				// Minutes in Portuguese
				d.endsWith(" minutos") || d.endsWith(" minuto") ||
				//Minutes in French
				d.endsWith(" minute") || d.endsWith(" minutes") -> parseRelativeDate(date)

			// Handle 'yesterday' and 'today', using midnight
			d.startsWith("year") -> Calendar.getInstance().apply {
				add(Calendar.DAY_OF_MONTH, -1) // yesterday
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			d.startsWith("today") -> Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 0)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}.timeInMillis

			date.contains(Regex("""\d(st|nd|rd|th)""")) -> date.split(" ").map {
				if (it.contains(Regex("""\d\D\D"""))) {
					it.replace(Regex("""\D"""), "")
				} else {
					it
				}
			}.let { dateFormat.tryParse(it.joinToString(" ")) }

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
				"days",
				"d",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h").anyWordIn(date) -> cal.apply {
				add(
					Calendar.HOUR,
					-number,
				)
			}.timeInMillis

			WordSet(
				"menit",
				"dakika",
				"min",
				"minute",
				"minutes",
				"minuto",
				"mins",
				"phút",
			).anyWordIn(date) -> cal.apply {
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

			WordSet("month", "months").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
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

	private companion object {

		private fun createRequestTemplate() =
			("action=madara_load_more&page=1&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5B" +
				"orderby%5D=meta_value_num&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query" +
				"%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5Brelation%5D=OR&vars%5Bpost_type" +
				"%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmeta_key%5D=_latest_update&vars%5Border" +
				"%5D=desc&vars%5Bmanga_archives_item_layout%5D=default").split(
				'&',
			).map {
				val pos = it.indexOf('=')
				it.substring(0, pos) to it.substring(pos + 1)
			}.toMutableMap()

		fun String.decodeHex(): ByteArray {
			check(length % 2 == 0) { "Must have an even length" }

			return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
		}

		const val URL_SEARCH_PREFIX = "slug:"
		val SALTED = "Salted__".toByteArray(Charsets.UTF_8)
	}
}
