package org.koitharu.kotatsu.parsers.site.all

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.insertCookies
import org.koitharu.kotatsu.parsers.util.mapChapters
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import org.koitharu.kotatsu.parsers.util.substringBetween
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toCamelCase
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.tryParse
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale

internal abstract class NineMangaParser(
	context: MangaLoaderContext,
	source: MangaSource,
	defaultDomain: String,
) : PagedMangaParser(context, source, pageSize = 26), Interceptor {

	override val configKeyDomain = ConfigKey.Domain(defaultDomain)

	init {
		context.cookieJar.insertCookies(domain, "ninemanga_template_desk=yes")
	}

	override val headers = super.headers.newBuilder()
		.add("Accept-Language", "en-US;q=0.7,en;q=0.3")
		.build()

	override val sortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.POPULARITY,
	)

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.url.host == domain) {
			request.newBuilder().removeHeader("Referer").build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!query.isNullOrEmpty() -> {
					append("/search/?name_sel=&wd=")
					append(query.urlEncoded())
					append("&page=")
				}

				!tags.isNullOrEmpty() -> {
					append("/search/?category_id=")
					for (tag in tags) {
						append(tag.key)
						append(',')
					}
					append("&page=")
				}

				else -> {
					append("/category/index_")
				}
			}
			append(page)
			append(".html")
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
		return manga.copy(
			tags = infoRoot.getElementsByAttributeValue("itemprop", "genre").first()?.select("a")?.mapToSet { a ->
				MangaTag(
					title = a.text().toTitleCase(),
					key = a.attr("href").substringBetween("/", "."),
					source = source,
				)
			}.orEmpty(),
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
						number = i + 1,
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

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://${domain}/search/?type=high").parseHtml()
		val root = doc.body().getElementById("search_form")
		return root?.select("li.cate_list")?.mapNotNullToSet { li ->
			val cateId = li.attr("cate_id") ?: return@mapNotNullToSet null
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			MangaTag(
				title = a.text().toTitleCase(),
				key = cateId,
				source = source,
			)
		} ?: doc.parseFailed("Root not found")
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
		MangaSource.NINEMANGA_EN,
		"www.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_ES", "NineManga Español", "es")
	class Spanish(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_ES,
		"es.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_RU", "NineManga Русский", "ru")
	class Russian(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_RU,
		"ru.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_DE", "NineManga Deutsch", "de")
	class Deutsch(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_DE,
		"de.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_BR", "NineManga Brasil", "pt")
	class Brazil(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_BR,
		"br.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_IT", "NineManga Italiano", "it")
	class Italiano(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_IT,
		"it.ninemanga.com",
	)

	@MangaSourceParser("NINEMANGA_FR", "NineManga Français", "fr")
	class Francais(context: MangaLoaderContext) : NineMangaParser(
		context,
		MangaSource.NINEMANGA_FR,
		"fr.ninemanga.com",
	)
}
