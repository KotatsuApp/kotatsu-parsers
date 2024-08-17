package org.koitharu.kotatsu.parsers.site.madara

import androidx.collection.scatterSetOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.AuthRequiredException
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class MadaraParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 12,
) : PagedMangaParser(context, source, pageSize), MangaParserAuthProvider {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val authUrl: String
		get() = "https://${domain}"

	override val isAuthorized: Boolean
		get() {
			return context.cookieJar.getCookies(domain).any {
				it.name.contains("wordpress_logged_in")
			}
		}

	override suspend fun getUsername(): String {
		val body = webClient.httpGet("https://${domain}/").parseHtml().body()
		return body.selectFirst(".c-user_name")?.text()
			?: run {
				throw if (body.selectFirst("#loginform") != null) {
					AuthRequiredException(source)
				} else {
					body.parseFailed("Cannot find username")
				}
			}
	}

	override val isMultipleTagsSupported = false

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
	)

	override val availableStates: Set<MangaState> = EnumSet.allOf(MangaState::class.java)

	override val availableContentRating: Set<ContentRating> = EnumSet.of(ContentRating.SAFE, ContentRating.ADULT)

	protected open val tagPrefix = "manga-genre/"
	protected open val datePattern = "MMMM d, yyyy"
	protected open val stylePage = "?style=list"
	protected open val postReq = false

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
	protected val ongoing = scatterSetOf(
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
	protected val finished = scatterSetOf(
		"Completed",
		"Complete",
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
		"Завершен",
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
		"منتهية",
	)

	@JvmField
	protected val abandoned = scatterSetOf(
		"Canceled",
		"Cancelled",
		"Cancelado",
		"cancellato",
		"Cancelados",
		"Dropped",
		"Discontinued",
		"abandonné",
		"Abandonné",
	)

	@JvmField
	protected val paused = scatterSetOf(
		"Hiatus",
		"On Hold",
		"Pausado",
		"En espera",
		"En pause",
		"En attente",
	)

	@JvmField
	protected val upcoming = scatterSetOf(
		"Upcoming",
		"upcoming",
		"لم تُنشَر بعد",
		"Prochainement",
		"À venir",
	)

	// Change these values only if the site does not support manga listings via ajax
	protected open val withoutAjax = false

	// can be changed to retrieve tags see getTags
	protected open val listUrl = "manga/"

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if (withoutAjax) {
			val pages = page + 1

			val url = buildString {
				append("https://")
				append(domain)

				when (filter) {

					is MangaListFilter.Search -> {
						if (pages > 1) {
							append("/page/")
							append(pages.toString())
						}
						append("/?s=")
						append(filter.query.urlEncoded())
						append("&post_type=wp-manga")
					}

					is MangaListFilter.Advanced -> {

						if (filter.tags.isNotEmpty()) {
							filter.tags.oneOrThrowIfMany()?.let {
								append("/$tagPrefix")
								append(it.key)
								if (pages > 1) {
									append("/page/")
									append(pages.toString())
								}
								append("/?")
							}
						} else {

							if (pages > 1) {
								append("/page/")
								append(pages.toString())
							}
							append("/?s=&post_type=wp-manga")
							filter.states.forEach {
								append("&status[]=")
								when (it) {
									MangaState.ONGOING -> append("on-going")
									MangaState.FINISHED -> append("end")
									MangaState.ABANDONED -> append("canceled")
									MangaState.PAUSED -> append("on-hold")
									MangaState.UPCOMING -> append("upcoming")
								}
							}

							filter.contentRating.oneOrThrowIfMany()?.let {
								append("&adult=")
								append(
									when (it) {
										ContentRating.SAFE -> "0"
										ContentRating.ADULT -> "1"
										else -> ""
									},
								)
							}

							append("&")
						}

						append("m_orderby=")
						when (filter.sortOrder) {
							SortOrder.POPULARITY -> append("views")
							SortOrder.UPDATED -> append("latest")
							SortOrder.NEWEST -> append("new-manga")
							SortOrder.ALPHABETICAL -> append("alphabet")
							SortOrder.RATING -> append("rating")
							else -> append("latest")
						}
					}

					null -> {
						append("?s&post_type=wp-manga&m_orderby=latest")
					}
				}
			}
			return parseMangaList(webClient.httpGet(url).parseHtml())
		} else {
			val payload = if (filter?.sortOrder == SortOrder.RATING) {
				createRequestTemplate(ratingRequest)
			} else {
				createRequestTemplate(defaultRequest)
			}

			payload["page"] = page.toString()

			when (filter) {

				is MangaListFilter.Search -> {
					payload["vars[s]"] = filter.query.urlEncoded()
				}

				is MangaListFilter.Advanced -> {

					filter.tags.oneOrThrowIfMany()?.let {
						payload["vars[wp-manga-genre]"] = it.key
					}

					when (filter.sortOrder) {
						SortOrder.POPULARITY -> payload["vars[meta_key]"] = "_wp_manga_views"
						SortOrder.UPDATED -> payload["vars[meta_key]"] = "_latest_update"
						SortOrder.NEWEST -> payload["vars[meta_key]"] = ""
						SortOrder.ALPHABETICAL -> {
							payload["vars[orderby]"] = "post_title"
							payload["vars[order]"] = "ASC"
						}

						SortOrder.RATING -> {}
						else -> payload["vars[meta_key]"] = "_latest_update"
					}

					filter.states.forEach {
						payload["vars[meta_query][0][0][value][]"] =
							when (it) {
								MangaState.ONGOING -> "on-going"
								MangaState.FINISHED -> "end"
								MangaState.ABANDONED -> "canceled"
								MangaState.PAUSED -> "on-hold"
								MangaState.UPCOMING -> "upcoming"
							}
					}

					filter.contentRating.oneOrThrowIfMany()?.let {
						payload["vars[meta_query][0][1][key]"] = "manga_adult_content"
						payload["vars[meta_query][0][1][value]"] =
							when (it) {
								ContentRating.SAFE -> ""
								ContentRating.ADULT -> "a%3A1%3A%7Bi%3A0%3Bs%3A3%3A%22yes%22%3B%7D"
								else -> ""
							}
					}
				}

				null -> {
					payload["vars[meta_key]"] = "_latest_update"
				}
			}

			return parseMangaList(
				webClient.httpPost(
					"https://$domain/wp-admin/admin-ajax.php",
					payload,
				).parseHtml(),
			)
		}
	}

	protected open fun parseMangaList(doc: Document): List<Manga> {
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
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4")
				?: div.selectFirst(".manga-name") ?: div.selectFirst(".post-title"))?.text().orEmpty(),
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
				state = when (
					summary?.selectFirst(".mg_status")
						?.selectFirst(".summary-content")
						?.ownText()
						.orEmpty()
				) {
					in ongoing -> MangaState.ONGOING
					in finished -> MangaState.FINISHED
					in abandoned -> MangaState.ABANDONED
					in paused -> MangaState.PAUSED
					in upcoming -> MangaState.UPCOMING
					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	override suspend fun getAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$listUrl").parseHtml()
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
				title = a.ownText().ifEmpty {
					a.selectFirst(".menu-image-title")?.textOrNull()
				}?.toTitleCase(sourceLocale) ?: return@mapNotNullToSet null,
				source = source,
			)
		}
	}

	protected open val selectDesc =
		"div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt, div.post-content div.manga-summary, div.post-content div.desc, div.c-page__content div.summary__content"
	protected open val selectGenre = "div.genres-content a"
	protected open val selectTestAsync = "div.listing-chapters_wrap"
	protected open val selectState = ""
	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val body = doc.body()

		val testCheckAsync = body.select(selectTestAsync)

		val chaptersDeferred = if (testCheckAsync.isNullOrEmpty()) {
			async { loadChapters(manga.url, doc) }
		} else {
			async { getChapters(manga, doc) }
		}

		val desc = body.select(selectDesc).html()

		val stateDiv = if (selectState.isEmpty()) {
			(body.selectFirst("div.post-content_item:contains(Status)")
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
		} else {
			body.selectFirst(selectState)
		}


		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			}
		}

		val alt =
			doc.body().select(".post-content_item:contains(Alt) .summary-content").firstOrNull()?.tableValue()?.text()
				?.trim() ?: doc.body().select(".post-content_item:contains(Nomes alternativos: ) .summary-content")
				.firstOrNull()?.tableValue()?.text()?.trim()

		manga.copy(
			tags = doc.body().select(selectGenre).mapNotNullToSet { a ->
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


	protected open val selectDate = "span.chapter-release-date i"
	protected open val selectChapter = "li.wp-manga-chapter"

	protected open suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				name = name,
				number = i + 1f,
				volume = 0,
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
		val doc = if (postReq) {
			val mangaId = document.select("div#manga-chapters-holder").attr("data-id")
			val url = "https://$domain/wp-admin/admin-ajax.php"
			val postData = "action=manga_get_chapters&manga=$mangaId"
			webClient.httpPost(url, postData).parseHtml()
		} else {
			val url = mangaUrl.toAbsoluteUrl(domain).removeSuffix('/') + "/ajax/chapters/"
			webClient.httpPost(url, emptyMap()).parseHtml()
		}
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirst("a")
			val href = a?.attrAsRelativeUrlOrNull("href") ?: li.parseFailed("Link is missing")
			val link = href + stylePage
			val dateText = li.selectFirst("a.c-new-tag")?.attr("title") ?: li.selectFirst(selectDate)?.text()
			val name = a.selectFirst("p")?.text() ?: a.ownText()
			MangaChapter(
				id = generateUid(href),
				url = link,
				name = name,
				number = i + 1f,
				volume = 0,
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

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow(".related-manga")
		return root.select("div.related-reading-wrap").mapNotNull { div ->
			val a = div.selectFirst("a") ?: return@mapNotNull null
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(a.host ?: domain),
				altTitle = null,
				title = div.selectFirstOrThrow(".widget-title").text(),
				author = null,
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				tags = emptySet(),
				rating = RATING_UNKNOWN,
				state = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	protected open val selectBodyPage = "div.main-col-inner div.reading-content"
	protected open val selectPage = "div.page-break"
	protected open val selectRequiredLogin = ".content-blocked, .login-required"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val chapterProtector = doc.getElementById("chapter-protector-data")
		if (chapterProtector == null) {
			throw if (doc.selectFirst(selectRequiredLogin) != null) {
				AuthRequiredException(source)
			} else {
				val root = doc.body().selectFirst(selectBodyPage) ?: throw ParseException(
					"No image found, try to log in",
					fullUrl,
				)
				return root.select(selectPage).map { div ->
					val img = div.selectFirstOrThrow("img")
					val url = img.src()?.toRelativeUrl(domain) ?: div.parseFailed("Image src not found")
					MangaPage(
						id = generateUid(url),
						url = url,
						preview = null,
						source = source,
					)
				}
			}
		} else {

			val chapterProtectorHtml = chapterProtector.attr("src")
				.takeIf { it.startsWith("data:text/javascript;base64,") }
				?.substringAfter("data:text/javascript;base64,")
				?.let {
					Base64.getDecoder().decode(it).decodeToString()
				}
				?: chapterProtector.html()

			val password = chapterProtectorHtml.substringAfter("wpmangaprotectornonce='").substringBefore("';")
			val chapterData = JSONObject(
				chapterProtectorHtml.substringAfter("chapter_data='").substringBefore("';").replace("\\/", "/"),
			)
			val unsaltedCiphertext = context.decodeBase64(chapterData.getString("ct"))
			val salt = chapterData.getString("s").toString().decodeHex()
			val ciphertext = "Salted__".toByteArray(Charsets.UTF_8) + salt + unsaltedCiphertext

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
				d.endsWith(" publicado") ||
				d.endsWith(" назад") || // other translated 'ago' in Russian
				d.endsWith(" önce") || // Handle translated 'ago' in Turkish.
				d.endsWith(" trước") || // Handle translated 'ago' in Viêt Nam.
				d.endsWith("مضت") || // Handle translated 'ago' in Arabic
				d.startsWith("منذ") ||
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
				d.endsWith(" minute") || d.endsWith(" minutes") ||
				//month in French
				d.endsWith(" mois") -> parseRelativeDate(date)

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
				"день",
			).anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet(
				"jam",
				"saat",
				"heure",
				"hora",
				"horas",
				"hour",
				"hours",
				"h",
				"ساعات",
				"ساعة",
			).anyWordIn(date) -> cal.apply {
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
				"минут",
				"دقيقة",
			).anyWordIn(date) -> cal.apply {
				add(
					Calendar.MINUTE,
					-number,
				)
			}.timeInMillis

			WordSet("detik", "segundo", "second", "ثوان").anyWordIn(date) -> cal.apply {
				add(
					Calendar.SECOND,
					-number,
				)
			}.timeInMillis

			WordSet("month", "months", "أشهر", "mois").anyWordIn(date) -> cal.apply {
				add(
					Calendar.MONTH,
					-number,
				)
			}.timeInMillis

			WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
			else -> 0
		}
	}

	private val ratingRequest =
		"action=madara_load_more&page=1&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5Borderby%5D%5Bquery_avarage_reviews%5D=DESC&vars%5Borderby%5D%5Bquery_total_reviews%5D=DESC&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5B0%5D%5Bquery_avarage_reviews%5D%5Bkey%5D=_manga_avarage_reviews&vars%5Bmeta_query%5D%5B0%5D%5Bquery_total_reviews%5D%5Bkey%5D=_manga_total_votes&vars%5Bmeta_query%5D%5Brelation%5D=AND&vars%5Bpost_type%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmanga_archives_item_layout%5D=default&vars%5Bmeta_query%5D%5B0%5D%5B0%5D%5Bkey%5D=_wp_manga_status&vars%5Bmeta_query%5D%5B0%5D%5B0%5D%5Bcompare%5D=IN"
	private val defaultRequest =
		"action=madara_load_more&page=1&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5Borderby%5D=meta_value_num&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5Brelation%5D=OR&vars%5Bpost_type%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmeta_key%5D=_latest_update&vars%5Border%5D=desc&vars%5Bmanga_archives_item_layout%5D=default&vars%5Bmeta_query%5D%5B0%5D%5B0%5D%5Bkey%5D=_wp_manga_status&vars%5Bmeta_query%5D%5B0%5D%5B0%5D%5Bcompare%5D=IN"

	private companion object {
		private fun createRequestTemplate(query: String) =
			(query).split(
				'&',
			).map {
				val pos = it.indexOf('=')
				it.substring(0, pos) to it.substring(pos + 1)
			}.toMutableMap()

		fun String.decodeHex(): ByteArray {
			check(length % 2 == 0) { "Must have an even length" }

			return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
		}

	}
}
