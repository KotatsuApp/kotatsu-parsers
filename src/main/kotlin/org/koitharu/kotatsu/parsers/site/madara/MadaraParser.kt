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

	// Change these values only if the site does not support manga listings via ajax
	protected open val withoutAjax = false

	override val availableSortOrders: Set<SortOrder> = setupAvailableSortOrders()

	private fun setupAvailableSortOrders(): Set<SortOrder> {
		return if (!withoutAjax) {
			EnumSet.of(
				SortOrder.UPDATED,
				SortOrder.UPDATED_ASC,
				SortOrder.POPULARITY,
				SortOrder.POPULARITY_ASC,
				SortOrder.NEWEST,
				SortOrder.NEWEST_ASC,
				SortOrder.ALPHABETICAL,
				SortOrder.ALPHABETICAL_DESC,
				SortOrder.RATING,
				SortOrder.RATING_ASC,
				SortOrder.RELEVANCE,
			)
		} else {
			EnumSet.of(
				SortOrder.UPDATED,
				SortOrder.POPULARITY,
				SortOrder.NEWEST,
				SortOrder.ALPHABETICAL,
				SortOrder.RATING,
				SortOrder.RELEVANCE,
			)
		}
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = !withoutAjax,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.allOf(MangaState::class.java),
		availableContentRating = EnumSet.of(ContentRating.SAFE, ContentRating.ADULT),
	)

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
		"en curso",
		"ongoing",
		"on going",
		"ativo",
		"en cours",
		"en cours \uD83D\uDFE2",
		"en cours de publication",
		"activo",
		"đang tiến hành",
		"em lançamento",
		"онгоінг",
		"publishing",
		"devam ediyor",
		"em andamento",
		"in corso",
		"güncel",
		"berjalan",
		"продолжается",
		"updating",
		"lançando",
		"in arrivo",
		"emision",
		"en emision",
		"مستمر",
		"curso",
		"en marcha",
		"publicandose",
		"publicando",
		"连载中",
	)

	@JvmField
	protected val finished = scatterSetOf(
		"completed",
		"complete",
		"completo",
		"complété",
		"fini",
		"achevé",
		"terminé",
		"terminé ⚫",
		"tamamlandı",
		"đã hoàn thành",
		"hoàn thành",
		"مكتملة",
		"завершено",
		"завершен",
		"finished",
		"finalizado",
		"completata",
		"one-shot",
		"bitti",
		"tamat",
		"completado",
		"concluído",
		"concluido",
		"已完结",
		"bitmiş",
		"end",
		"منتهية",
	)

	@JvmField
	protected val abandoned = scatterSetOf(
		"canceled",
		"cancelled",
		"cancelado",
		"cancellato",
		"cancelados",
		"dropped",
		"discontinued",
		"abandonné",
	)

	@JvmField
	protected val paused = scatterSetOf(
		"hiatus",
		"on hold",
		"pausado",
		"en espera",
		"en pause",
		"en attente",
	)

	@JvmField
	protected val upcoming = scatterSetOf(
		"upcoming",
		"لم تُنشَر بعد",
		"prochainement",
		"à venir",
	)

	// can be changed to retrieve tags see getTags
	protected open val listUrl = "manga/"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (withoutAjax) {
			val pages = page + 1

			val url = buildString {
				append("https://")
				append(domain)

				if (pages > 1) {
					append("/page/")
					append(pages.toString())
				}
				append("/?s=")

				filter.query?.let {
					append(filter.query.urlEncoded())
				}

				append("&post_type=wp-manga")

				// Known bug: in some cases, if there are no manga with the associated tags, the source returns the full list of manga
				if (filter.tags.isNotEmpty()) {
					filter.tags.forEach {
						append("&genre[]=")
						append(it.key)
					}
				}

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

				if (filter.year != 0) {
					append("&release=")
					append(filter.year.toString())
				}

				// Support author
				//filter.author?.let {
				//	append("&author=")
				//	append(filter.author)
				//}

				// Support artist
				//filter.artist?.let {
				//	append("&artist=")
				//	append(filter.artist)
				//}


				append("&m_orderby=")
				when (order) {
					SortOrder.POPULARITY -> append("views")
					SortOrder.UPDATED -> append("latest")
					SortOrder.NEWEST -> append("new-manga")
					SortOrder.ALPHABETICAL -> append("alphabet")
					SortOrder.RATING -> append("rating")
					SortOrder.RELEVANCE -> {}
					else -> {}
				}
			}
			return parseMangaList(webClient.httpGet(url).parseHtml())
		} else {

			val payload = createRequestTemplate()

			payload["page"] = page.toString()

			filter.query?.let {
				payload["vars[s]"] = filter.query.urlEncoded()
			}

			if (filter.tags.isNotEmpty()) {
				payload["vars[tax_query][0][taxonomy]"] = "wp-manga-genre"
				payload["vars[tax_query][0][field]"] = "slug"
				filter.tags.forEachIndexed { i, it ->
					payload["vars[tax_query][0][terms][$i]"] = it.key
				}
				payload["vars[tax_query][0][operator]"] = "IN"
			}

			if (filter.tagsExclude.isNotEmpty()) {
				payload["vars[tax_query][1][taxonomy]"] = "wp-manga-genre"
				payload["vars[tax_query][1][field]"] = "slug"
				filter.tagsExclude.forEachIndexed { i, it ->
					payload["vars[tax_query][1][terms][$i]"] = it.key
				}
				payload["vars[tax_query][1][operator]"] = "NOT IN"
			}

			if (filter.year != 0) {
				payload["vars[tax_query][2][taxonomy]"] = "wp-manga-release"
				payload["vars[tax_query][2][field]"] = "slug"
				payload["vars[tax_query][2][terms][]"] = filter.year.toString()
			}

			// Support author
			//  filter.author.let {
			//	payload["vars[tax_query][3][taxonomy]"] = "wp-manga-author"
			//	payload["vars[tax_query][3][field]"] = "name"
			//	payload["vars[tax_query][3][terms][0]"] = filter.author
			//	payload["vars[tax_query][3][operator]"] = "IN"
			//}


			// Support artist
			//  filter.artist.let {
			//	payload["vars[tax_query][4][taxonomy]"] = "wp-manga-artist"
			//	payload["vars[tax_query][4][field]"] = "name"
			//	payload["vars[tax_query][4][terms][0]"] = filter.artist
			//	payload["vars[tax_query][4][operator]"] = "IN"
			//}

			if (filter.tags.isNotEmpty() || filter.tagsExclude.isNotEmpty() || filter.year != 0) {
				payload["vars[tax_query][relation]"] = "AND"
			}

			when (order) {
				SortOrder.POPULARITY -> {
					payload["vars[meta_key]"] = "_wp_manga_views"
					payload["vars[orderby]"] = "meta_value_num"
					payload["vars[order]"] = "desc"
				}

				SortOrder.POPULARITY_ASC -> {
					payload["vars[meta_key]"] = "_wp_manga_views"
					payload["vars[orderby]"] = "meta_value_num"
					payload["vars[order]"] = "asc"
				}

				SortOrder.UPDATED -> {
					payload["vars[meta_key]"] = "_latest_update"
					payload["vars[orderby]"] = "meta_value_num"
					payload["vars[order]"] = "desc"
				}

				SortOrder.UPDATED_ASC -> {
					payload["vars[meta_key]"] = "_latest_update"
					payload["vars[orderby]"] = "meta_value_num"
					payload["vars[order]"] = "asc"
				}

				SortOrder.NEWEST -> {
					payload["vars[orderby]"] = "date"
					payload["vars[order]"] = "desc"
				}

				SortOrder.NEWEST_ASC -> {
					payload["vars[orderby]"] = "date"
					payload["vars[order]"] = "asc"
				}

				SortOrder.ALPHABETICAL -> {
					payload["vars[orderby]"] = "post_title"
					payload["vars[order]"] = "asc"
				}

				SortOrder.ALPHABETICAL_DESC -> {
					payload["vars[orderby]"] = "post_title"
					payload["vars[order]"] = "desc"
				}

				SortOrder.RATING -> {
					payload["vars[meta_query][0][query_avarage_reviews][key]"] = "_manga_avarage_reviews"
					payload["vars[meta_query][0][query_total_reviews][key]"] = "_manga_total_votes"

					payload["vars[orderby][query_avarage_reviews]"] = "DESC"
					payload["vars[orderby][query_total_reviews]"] = "DESC"
				}

				SortOrder.RATING_ASC -> {
					payload["vars[meta_query][0][query_avarage_reviews][key]"] = "_manga_avarage_reviews"
					payload["vars[meta_query][0][query_total_reviews][key]"] = "_manga_total_votes"

					payload["vars[orderby][query_avarage_reviews]"] = "ASC"
					payload["vars[orderby][query_total_reviews]"] = "ASC"
				}

				SortOrder.RELEVANCE -> {
					payload["vars[orderby]"] = ""
				}

				else -> payload["vars[orderby]"] = ""
			}

			filter.states.forEach {
				payload["vars[meta_query][0][0][key]"] = "_wp_manga_status"
				payload["vars[meta_query][0][0][compare]"] = "IN"
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
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3, h4") ?: div.selectFirst(".manga-name, .post-title"))?.text()
					.orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: RATING_UNKNOWN,
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
						?.ownText()?.lowercase()
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

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
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
			val href = a.attr("href").removeSuffix('/').substringAfterLast(tagPrefix, "")
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
	protected open val selectState =
		"div.post-content_item:contains(Status), div.post-content_item:contains(Statut), " +
			"div.post-content_item:contains(État), div.post-content_item:contains(حالة العمل), div.post-content_item:contains(Estado), div.post-content_item:contains(สถานะ)," +
			"div.post-content_item:contains(Stato), div.post-content_item:contains(Durum), div.post-content_item:contains(Statüsü), div.post-content_item:contains(Статус)," +
			"div.post-content_item:contains(状态), div.post-content_item:contains(الحالة)"
	protected open val selectAlt =
		".post-content_item:contains(Alt) .summary-content, .post-content_item:contains(Nomes alternativos: ) .summary-content"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val href = doc.selectFirst("head meta[property='og:url']")?.attr("content")?.toRelativeUrl(domain) ?: manga.url
		val testCheckAsync = doc.select(selectTestAsync)
		val chaptersDeferred = if (testCheckAsync.isNullOrEmpty()) {
			async { loadChapters(href, doc) }
		} else {
			async { getChapters(manga, doc) }
		}

		val desc = doc.select(selectDesc).html()

		val stateDiv = doc.selectFirst(selectState)?.selectLast("div.summary-content")

		val state = stateDiv?.let {
			when (it.text().lowercase()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in abandoned -> MangaState.ABANDONED
				in paused -> MangaState.PAUSED
				else -> null
			}
		}

		val alt = doc.body().select(selectAlt).firstOrNull()?.tableValue()?.textOrNull()

		manga.copy(
			title = doc.selectFirst("h1")?.textOrNull() ?: manga.title,
			url = href,
			publicUrl = href.toAbsoluteUrl(domain),
			tags = doc.body().select(selectGenre).mapToSet { a ->
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
			isNsfw = doc.selectFirst(".adult-confirm") != null,
		)
	}


	protected open val selectDate = "span.chapter-release-date i"
	protected open val selectChapter = "li.wp-manga-chapter"

	protected open suspend fun getChapters(manga: Manga, doc: Document): List<MangaChapter> {
		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)
		return doc.body().select(selectChapter).mapChapters(reversed = true) { i, li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
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
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsRelativeUrl("href")
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
				return root.select(selectPage).flatMap { div ->
					div.selectOrThrow("img").map { img ->
						val url = img.requireSrc().toRelativeUrl(domain)
						MangaPage(
							id = generateUid(url),
							url = url,
							preview = null,
							source = source,
						)
					}
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
		val d = date?.lowercase() ?: return 0
		return when {

			WordSet(
				" ago", "atrás", " hace", " publicado", " назад", " önce", " trước", "مضت",
				" h", " d", " días", " jour", " horas", " heure", " mins", " minutos", " minute", " mois",
			).endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("há ", "منذ", "il y a").startsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("yesterday", "يوم واحد").startsWith(d) -> {
				Calendar.getInstance().apply {
					add(Calendar.DAY_OF_MONTH, -1) // yesterday
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

			WordSet("today").startsWith(d) -> {
				Calendar.getInstance().apply {
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

			WordSet("يومين").startsWith(d) -> {
				Calendar.getInstance().apply {
					add(Calendar.DAY_OF_MONTH, -2) // day before yesterday
					set(Calendar.HOUR_OF_DAY, 0)
					set(Calendar.MINUTE, 0)
					set(Calendar.SECOND, 0)
					set(Calendar.MILLISECOND, 0)
				}.timeInMillis
			}

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

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("detik", "segundo", "second", "ثوان")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("menit", "dakika", "min", "minute", "minutes", "minuto", "mins", "phút", "минут", "دقيقة")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("jam", "saat", "heure", "hora", "horas", "hour", "hours", "h", "ساعات", "ساعة")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("hari", "gün", "jour", "día", "dia", "day", "días", "days", "d", "день")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months", "أشهر", "mois", "meses", "mes")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}

	private companion object {

		private fun createRequestTemplate() =
			("action=madara_load_more&page=0&template=madara-core%2Fcontent%2Fcontent-search&vars%5Bs%5D=&vars%5Bpaged%5D=1&vars%5Btemplate%5D=search&vars%5Bmeta_query%5D%5B0%5D%5Brelation%5D=AND&vars%5Bmeta_query%5D%5Brelation%5D=AND&vars%5Bpost_type%5D=wp-manga&vars%5Bpost_status%5D=publish&vars%5Bmanga_archives_item_layout%5D=default").split(
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
