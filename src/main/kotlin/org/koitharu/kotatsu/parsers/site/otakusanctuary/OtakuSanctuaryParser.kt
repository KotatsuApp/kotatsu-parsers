package org.koitharu.kotatsu.parsers.site.otakusanctuary

import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

internal abstract class OtakuSanctuaryParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 32,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.NEWEST,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	protected open val listUrl = "Manga/Newest"
	protected open val datePattern = "dd/MM/yyyy"
	protected open val lang = ""

	@JvmField
	protected val ongoing: Set<String> = setOf(
		"Ongoing",
	)

	@JvmField
	protected val finished: Set<String> = setOf(
		"Completed",
		"Done",
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val doc = when {
			!filter.query.isNullOrEmpty() -> {
				if (page > 1) {
					return emptyList()
				}
				val url = buildString {
					append("https://")
					append(domain)
					append("/Home/Search?search=")
					append(filter.query.urlEncoded())
				}
				webClient.httpGet(url).parseHtml().requireElementById("collection-manga")
			}

			else -> {
				if (filter.tags.isNotEmpty()) {
					val url = buildString {
						append("https://")
						append(domain)
						append("/Genre/MangaGenrePartial?id=")
						filter.tags.oneOrThrowIfMany()?.let {
							append(it.key)
						}
						append("&lang=")
						append(lang)
						append("&offset=")
						append(page * pageSize)
						append("&pagesize=")
						append(pageSize)
					}
					webClient.httpGet(url).parseHtml()

				} else {
					val payload = HashMap<String, String>()
					payload["Lang"] = lang
					payload["Page"] = page.toString()
					payload["Type"] = "Include"
					when (order) {
						SortOrder.NEWEST -> payload["Dir"] = "CreatedDate"
						SortOrder.UPDATED -> payload["Dir"] = "NewPostedDate"
						else -> payload["Dir"] = "NewPostedDate"
					}
					webClient.httpPost("https://$domain/$listUrl", payload).parseHtml()
				}

			}
		}


		return doc.select("div.picture-card").map { div ->
			val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst("h4")?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst(".rating")?.ownText()?.toFloatOrNull()?.div(10f) ?: RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	protected open val selectBodyTag = "div#genre-table a"

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/Home/LoadingGenresMenu").parseHtml()
		return doc.select(selectBodyTag).mapToSet { a ->
			val href = a.attr("href").substringAfterLast('/').substringBefore('?')
			MangaTag(
				key = href,
				title = a.text(),
				source = source,
			)
		}
	}

	protected open val selectDesc = "div.summary"
	protected open val selectState = ".table-info tr:contains(Tình Trạng) td"
	protected open val selectAlt = ".table-info tr:contains(Other Name) + tr"
	protected open val selectAut = ".table-info tr a.capitalize"
	protected open val selectTag = ".genres a"

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		val desc = doc.selectFirst(selectDesc)?.html()

		val stateDiv = doc.selectFirst(selectState)

		val state = stateDiv?.let {
			when (it.text()) {
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				else -> null
			}
		}

		val alt = doc.body().selectFirst(selectAlt)?.text()?.replace("Other names", "")
		val auth = doc.body().selectFirst(selectAut)?.text()

		val dateFormat = SimpleDateFormat(datePattern, sourceLocale)

		manga.copy(
			tags = doc.body().select(selectTag).mapToSet { a ->
				val href = a.attr("href").substringAfterLast('/').substringBefore('?')
				MangaTag(
					key = href,
					title = a.text(),
					source = source,
				)
			},
			description = desc,
			altTitle = alt,
			author = auth,
			state = state,
			chapters = doc.body().requireElementById("chapter").select("tr.chapter")
				.mapChapters(reversed = true) { i, tr ->
					val dateText = tr.select("td")[3].text()
					val a = tr.selectFirstOrThrow("td.read-chapter a")
					val url = a.attrAsRelativeUrl("href")
					val name = a.text()
					MangaChapter(
						id = generateUid(url),
						name = name,
						number = i.toFloat(),
						volume = 0,
						url = url,
						scanlator = null,
						uploadDate = parseChapterDate(
							dateFormat,
							dateText,
						),
						branch = null,
						source = source,
					)
				},
		)
	}


	protected open val selectPage = "div#rendering .image-wraper img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()

		if (doc.select(selectPage).attr("src").isNullOrEmpty()) {
			val chapterId = doc.select("#inpit-c").attr("data-chapter-id")
			val url = "https://$domain/Manga/UpdateView"
			val postdata = "chapId=$chapterId"

			val json = webClient.httpPost(url, postdata).parseRaw()

			val urls = json.replace("\\u0022", "").substringAfter("{\"view\":\"[").substringBefore("]\",\"isSuccess")
				.split(",")
			return urls.map {
				val urlImage = processUrl(it)
				MangaPage(
					id = generateUid(urlImage),
					url = urlImage,
					preview = null,
					source = source,
				)
			}
		} else {
			return doc.select(selectPage).map { img ->
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

	protected fun parseChapterDate(dateFormat: DateFormat, date: String?): Long {
		val d = date?.lowercase() ?: return 0
		return when {
			WordSet(" ago", " atrás").endsWith(d) -> {
				parseRelativeDate(d)
			}

			WordSet("cách đây ").startsWith(d) -> {
				parseRelativeDate(d)
			}

			else -> dateFormat.tryParse(date)
		}
	}

	private fun parseRelativeDate(date: String): Long {
		val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
		val cal = Calendar.getInstance()
		return when {
			WordSet("second", "giây")
				.anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis

			WordSet("min", "minute", "minutes", "phút")
				.anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis

			WordSet("tiếng", "hour", "hours")
				.anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis

			WordSet("day", "days", "d", "ngày")
				.anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis

			WordSet("month", "months")
				.anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis

			WordSet("year")
				.anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis

			else -> 0
		}
	}


	@Suppress("NAME_SHADOWING")
	private fun processUrl(url: String, vi: String = ""): String {
		var url = url.replace("_h_", "http")
			.replace("_e_", "/extendContent/Manga")
			.replace("_r_", "/extendContent/MangaRaw")

		if (url.startsWith("//")) {
			url = "https:$url"
		}
		if (url.contains("drive.google.com")) {
			return url
		}

		url = when (url.slice(0..4)) {
			"[GDP]" -> url.replace("[GDP]", "https://drive.google.com/uc?export=view&id=")
			"[GDT]" -> if (lang == "us") {
				url.replace("image2.otakuscan.net", "image3.shopotaku.net")
					.replace("image2.otakusan.net", "image3.shopotaku.net")
			} else {
				url
			}

			"[IS1]" -> {
				val url1 = url.replace("[IS1]", "https://imagepi.otakuscan.net/")
				if (url1.contains("vi") && url1.contains("otakusan.net_")) {
					url1
				} else {
					url1.toHttpUrl().newBuilder().apply {
						addQueryParameter("vi", vi)
					}.build().toString()
				}
			}

			"[IS3]" -> url.replace("[IS3]", "https://image3.otakusan.net/")
			"[IO3]" -> url.replace("[IO3]", "http://image3.shopotaku.net/")
			else -> url
		}

		if (url.contains("/Content/Workshop") || url.contains("otakusan") || url.contains("myrockmanga")) {
			return url
		}

		if (url.contains("file-bato-orig.anyacg.co")) {
			url = url.replace("file-bato-orig.anyacg.co", "file-bato-orig.bato.to")
		}

		if (url.contains("file-comic")) {
			if (url.contains("file-comic-1")) {
				url = url.replace("file-comic-1.anyacg.co", "z-img-01.mangapark.net")
			}
			if (url.contains("file-comic-2")) {
				url = url.replace("file-comic-2.anyacg.co", "z-img-02.mangapark.net")
			}
			if (url.contains("file-comic-3")) {
				url = url.replace("file-comic-3.anyacg.co", "z-img-03.mangapark.net")
			}
			if (url.contains("file-comic-4")) {
				url = url.replace("file-comic-4.anyacg.co", "z-img-04.mangapark.net")
			}
			if (url.contains("file-comic-5")) {
				url = url.replace("file-comic-5.anyacg.co", "z-img-05.mangapark.net")
			}
			if (url.contains("file-comic-6")) {
				url = url.replace("file-comic-6.anyacg.co", "z-img-06.mangapark.net")
			}
			if (url.contains("file-comic-9")) {
				url = url.replace("file-comic-9.anyacg.co", "z-img-09.mangapark.net")
			}
			if (url.contains("file-comic-10")) {
				url = url.replace("file-comic-10.anyacg.co", "z-img-10.mangapark.net")
			}
			if (url.contains("file-comic-99")) {
				url = url.replace("file-comic-99.anyacg.co/uploads", "file-bato-0001.bato.to")
			}
		}

		if (url.contains("cdn.nettruyen.com")) {
			url = url.replace(
				"cdn.nettruyen.com/Data/Images/",
				"truyen.cloud/data/images/",
			)
		}
		if (url.contains("url=")) {
			url = url.substringAfter("url=")
		}
		if (url.contains("blogspot") || url.contains("fshare")) {
			url = url.replace("http:", "https:")
		}
		if (url.contains("blogspot") && !url.contains("http")) {
			url = "https://$url"
		}
		if (url.contains("app/manga/uploads/") && !url.contains("http")) {
			url = "https://lhscan.net$url"
		}
		url = url.replace("//cdn.adtrue.com/rtb/async.js", "")

		if (url.contains(".webp")) {
			url = "https://otakusan.net/api/Value/ImageSyncing?ip=34512351".toHttpUrl().newBuilder()
				.apply {
					addQueryParameter("url", url)
				}.build().toString()
		} else if (
			(
				url.contains("merakiscans") ||
					url.contains("mangazuki") ||
					url.contains("ninjascans") ||
					url.contains("anyacg.co") ||
					url.contains("mangakatana") ||
					url.contains("zeroscans") ||
					url.contains("mangapark") ||
					url.contains("mangadex") ||
					url.contains("uptruyen") ||
					url.contains("hocvientruyentranh") ||
					url.contains("ntruyen.info") ||
					url.contains("chancanvas") ||
					url.contains("bato.to")
				) &&
			(
				!url.contains("googleusercontent") &&
					!url.contains("otakusan") &&
					!url.contains("otakuscan") &&
					!url.contains("shopotaku")
				)
		) {
			url =
				"https://images2-focus-opensocial.googleusercontent.com/gadgets/proxy?container=focus&gadget=a&no_expand=1&resize_h=0&rewriteMime=image%2F*".toHttpUrl()
					.newBuilder().apply {
						addQueryParameter("url", url)
					}.build().toString()
		} else if (url.contains("imageinstant.com")) {
			url = "https://images.weserv.nl/".toHttpUrl().newBuilder().apply {
				addQueryParameter("url", url)
			}.build().toString()
		} else if (!url.contains("otakusan.net")) {
			url = "https://otakusan.net/api/Value/ImageSyncing?ip=34512351".toHttpUrl().newBuilder()
				.apply {
					addQueryParameter("url", url)
				}.build().toString()
		}

		return if (url.contains("vi=") && !url.contains("otakusan.net_")) {
			url
		} else {
			url.toHttpUrl().newBuilder().apply {
				addQueryParameter("vi", vi)
			}.build().toString()
		}
	}
}
