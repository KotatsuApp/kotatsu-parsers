package org.koitharu.kotatsu.parsers.site.all

import androidx.collection.ArraySet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("IMHENTAI", "ImHentai", type = ContentType.HENTAI)
internal class ImHentai(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.IMHENTAI, pageSize = 20) {

	override val availableSortOrders: Set<SortOrder> =
		EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.RATING)

	override val configKeyDomain = ConfigKey.Domain("imhentai.xxx")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search/?page=")
			append(page.toString())
			when (filter) {
				is MangaListFilter.Search -> {
					append("&key=")
					append(filter.query.urlEncoded())
				}

				is MangaListFilter.Advanced -> {

					if (filter.tags.isNotEmpty()) {
						append("&key=")
						append(filter.tags.joinToString(separator = ",") { it.key })
					}

					var lang = "&en=1&jp=1&es=1&fr=1&kr=1&de=1&ru=1"
					filter.locale?.let {
						lang = "&en=0&jp=0&es=0&fr=0&kr=0&de=0&ru=0"
						lang = lang.replace("${it.language}=0", "${it.language}=1")
					}
					append(lang)

					when (filter.sortOrder) {
						SortOrder.UPDATED -> append("&lt=1&pp=0")
						SortOrder.POPULARITY -> append("&lt=0&pp=1")
						SortOrder.RATING -> append("&lt=0&pp=0")
						else -> append("&lt=1&pp=0")
					}
				}

				null -> {
					append("&lt=1&pp=0")
				}
			}

		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.galleries div.thumb").map { div ->
			val a = div.selectFirstOrThrow(".inner_thumb a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = a.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst(".caption")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}

	//Tags are deliberately reduced because there are too many and this slows down the application.
	//only the most popular ones are taken.
	override suspend fun getAvailableTags(): Set<MangaTag> {
		return coroutineScope {
			(1..3).map { page ->
				async { getTags(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun getTags(page: Int): Set<MangaTag> {
		val url = "https://$domain/tags/popular/?page=$page"
		val root = webClient.httpGet(url).parseHtml()
		return root.parseTags()
	}

	private fun Element.parseTags() = select("div.stags a.tag_btn").mapToSet {
		val href = it.attr("href").substringAfterLast("tag/").substringBeforeLast('/')
		MangaTag(
			key = href,
			title = it.selectFirstOrThrow("h3.list_tag").text().toTitleCase(sourceLocale),
			source = source,
		)
	}

	override suspend fun getAvailableLocales(): Set<Locale> = setOf(
		Locale.ENGLISH, Locale.JAPANESE, Locale("es"), Locale.FRENCH, Locale("kr"), Locale.GERMAN, Locale("ru"),
	)

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		manga.copy(
			tags = doc.body().select("li:contains(Tags) a.tag").mapNotNullToSet {
				val href = it.attr("href").substringAfterLast("tag/").substringBeforeLast('/')
				val name = it.html().substringBeforeLast("<span")
				MangaTag(
					key = href,
					title = name,
					source = source,
				)
			},
			author = doc.selectFirst("li:contains(Artists) a.tag")?.html()?.substringBefore("<span"),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = 0,
					branch = doc.selectFirst("li:contains(Language) a.tag")?.html()?.substringBeforeLast("<span"),
					source = source,
				),
			),
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().selectFirstOrThrow("div.related")
		return root.select("div.thumb").mapNotNull { div ->
			val a = div.selectFirstOrThrow(".inner_thumb a")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				coverUrl = a.selectFirst("img")?.src().orEmpty(),
				title = div.selectFirst(".caption")?.text().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = null,
				state = null,
				source = source,
				isNsfw = false,
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val totalPages = doc.selectFirstOrThrow(".pages").text().replace("Pages: ", "").toInt() + 1
		val domainImg = doc.requireElementById("append_thumbs").selectFirstOrThrow("img").src()?.replace("1t.jpg", "")
		val pages = ArrayList<MangaPage>(totalPages)
		for (i in 1 until totalPages) {
			val url = "$domainImg$i.jpg"
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = null,
					source = source,
				),
			)
		}
		return pages
	}
}
