package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArrayMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

internal abstract class NineMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	defaultDomain: String,
) : PagedMangaParser(context, source, pageSize = 26), Interceptor {

	override val configKeyDomain = ConfigKey.Domain(defaultDomain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	init {
		context.cookieJar.insertCookies(domain, "ninemanga_template_desk=yes")
	}

	override fun getRequestHeaders() = super.getRequestHeaders().newBuilder()
		.add("Accept-Language", "en-US;q=0.7,en;q=0.3")
		.build()

	override val availableSortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.POPULARITY,
	)

	override val availableStates: Set<MangaState> = EnumSet.of(
		MangaState.ONGOING,
		MangaState.FINISHED,
	)

	override val isTagsExclusionSupported: Boolean = true

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.url.host == domain) {
			request.newBuilder().removeHeader("Referer").build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when (filter) {
				is MangaListFilter.Search -> {
					append("/search/?name_sel=&wd=")
					append(filter.query.urlEncoded())
					append("&page=")
					append(page)
					append(".html")
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty() || filter.tagsExclude.isNotEmpty() || filter.states.isNotEmpty()) {
						append("/search/?category_id=")
						append(filter.tags.joinToString(separator = ",") { it.key })

						append("&out_category_id=")
						append(filter.tagsExclude.joinToString(separator = ",") { it.key })

						filter.states.oneOrThrowIfMany()?.let {
							append("&completed_series=")
							when (it) {
								MangaState.ONGOING -> append("no")
								MangaState.FINISHED -> append("yes")
								else -> append("either")
							}
						}
						append("&page=")
					} else {
						append("/category/index_")
					}
					append(page.toString())
					append(".html")
				}

				null -> {
					append("/category/index_")
					append(page)
					append(".html")
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirst("ul.direlist") ?: doc.parseFailed("Cannot find root")
		val baseHost = root.baseUri().toHttpUrl().host
		return root.select("li").map { node ->
			val href = node.selectFirst("a")?.absUrl("href") ?: node.parseFailed("Link not found")
			val relUrl = href.toRelativeUrl(baseHost)
			val dd = node.selectFirst("dd")
			Manga(
				id = generateUid(relUrl),
				url = relUrl,
				publicUrl = href,
				title = dd?.selectFirst("a.bookname")?.text()?.toCamelCase().orEmpty(),
				altTitle = null,
				coverUrl = node.selectFirst("img")?.absUrl("src").orEmpty(),
				rating = RATING_UNKNOWN,
				author = null,
				isNsfw = false,
				tags = emptySet(),
				state = null,
				source = source,
				description = dd?.selectFirst("p")?.html(),
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(
			manga.url.toAbsoluteUrl(domain) + "?waring=1",
		).parseHtml()
		val root = doc.body().selectFirstOrThrow("div.manga")
		val infoRoot = root.selectFirstOrThrow("div.bookintro")
		val tagMap = getOrCreateTagMap()
		val selectTag = infoRoot.getElementsByAttributeValue("itemprop", "genre").first()?.select("a")
		val tags = selectTag?.mapNotNullToSet { tagMap[it.text()] }
		return manga.copy(
			tags = tags.orEmpty(),
			author = infoRoot.getElementsByAttributeValue("itemprop", "author").first()?.text(),
			state = parseStatus(infoRoot.select("li a.red").text()),
			description = infoRoot.getElementsByAttributeValue("itemprop", "description").first()?.html()
				?.substringAfter("</b>"),
			chapters = root.selectFirst("div.chapterbox")?.select("ul.sub_vol_ul > li")
				?.mapChapters(reversed = true) { i, li ->
					val a = li.selectFirst("a.chapter_list_a")
					val href =
						a?.attrAsRelativeUrlOrNull("href")?.replace("%20", " ") ?: li.parseFailed("Link not found")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						uploadDate = parseChapterDateByLang(li.selectFirst("span")?.text().orEmpty()),
						source = source,
						scanlator = null,
						branch = null,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.body().getElementById("page")?.select("option")?.map { option ->
			val url = option.attr("value")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		} ?: doc.parseFailed("Pages list not found")
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body()
		return root.selectFirst("a.pic_download")?.absUrl("href") ?: doc.parseFailed("Page image not found")
	}

	private var tagCache: ArrayMap<String, MangaTag>? = null
	private val mutex = Mutex()

	override suspend fun getAvailableTags(): Set<MangaTag> {
		return getOrCreateTagMap().values.toSet()
	}

	private suspend fun getOrCreateTagMap(): Map<String, MangaTag> = mutex.withLock {
		tagCache?.let { return@withLock it }
		val tagMap = ArrayMap<String, MangaTag>()
		val tagElements = webClient.httpGet("https://${domain}/search/?type=high").parseHtml().select("li.cate_list")
		for (el in tagElements) {
			if (el.text().isEmpty()) continue
			val cateId = el.attr("cate_id")
			val a = el.selectFirstOrThrow("a")
			tagMap[el.text()] = MangaTag(
				title = a.text().toTitleCase(sourceLocale),
				key = cateId,
				source = source,
			)
		}
		tagCache = tagMap
		return@withLock tagMap
	}

	private fun parseStatus(status: String) = when {
		// en
		status.contains("Ongoing") -> MangaState.ONGOING
		status.contains("Completed") -> MangaState.FINISHED
		//es
		status.contains("En curso") -> MangaState.ONGOING
		status.contains("Completado") -> MangaState.FINISHED
		//ru
		status.contains("постоянный") -> MangaState.ONGOING
		status.contains("завершенный") -> MangaState.FINISHED
		//de
		status.contains("Laufende") -> MangaState.ONGOING
		status.contains("Abgeschlossen") -> MangaState.FINISHED
		//pt
		status.contains("Completo") -> MangaState.ONGOING
		status.contains("Em tradução") -> MangaState.FINISHED
		//it
		status.contains("In corso") -> MangaState.ONGOING
		status.contains("Completato") -> MangaState.FINISHED
		//fr
		status.contains("En cours") -> MangaState.ONGOING
		status.contains("Complété") -> MangaState.FINISHED
		else -> null
	}

	private fun parseChapterDateByLang(date: String): Long {
		val dateWords = date.split(" ")

		if (dateWords.size == 3) {
			if (dateWords[1].contains(",")) {
				SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).tryParse(date)
			} else {
				val timeAgo = Integer.parseInt(dateWords[0])
				return Calendar.getInstance().apply {
					when (dateWords[1]) {
						"minutes" -> Calendar.MINUTE // EN-FR
						"hours" -> Calendar.HOUR // EN

						"minutos" -> Calendar.MINUTE // ES
						"horas" -> Calendar.HOUR

						// "minutos" -> Calendar.MINUTE // BR
						"hora" -> Calendar.HOUR

						"минут" -> Calendar.MINUTE // RU
						"часа" -> Calendar.HOUR

						"Stunden" -> Calendar.HOUR // DE

						"minuti" -> Calendar.MINUTE // IT
						"ore" -> Calendar.HOUR

						"heures" -> Calendar.HOUR // FR ("minutes" also French word)
						else -> null
					}?.let {
						add(it, -timeAgo)
					}
				}.timeInMillis
			}
		}
		return 0L
	}

	@MangaSourceParser("NINEMANGA_EN", "NineManga English", "en")
	class English(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_EN,
		"www.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_ES", "NineManga Español", "es")
	class Spanish(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_ES,
		"es.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_RU", "NineManga Русский", "ru")
	class Russian(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_RU,
		"ru.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_DE", "NineManga Deutsch", "de")
	class Deutsch(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_DE,
		"de.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_BR", "NineManga Brasil", "pt")
	class Brazil(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_BR,
		"br.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_IT", "NineManga Italiano", "it")
	class Italiano(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_IT,
		"it.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_FR", "NineManga Français", "fr")
	class Francais(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaParserSource.NINEMANGA_FR,
		"fr.ninemanga.com",
	)
}
