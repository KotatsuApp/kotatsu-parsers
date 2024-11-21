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

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableLocales = setOf(
			Locale.ENGLISH,
			Locale.JAPANESE,
			Locale("es"),
			Locale.FRENCH,
			Locale("kr"),
			Locale.GERMAN,
			Locale("ru"),
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.DOUJINSHI,
			ContentType.COMICS,
			ContentType.IMAGE_SET,
			ContentType.ARTIST_CG,
			ContentType.GAME_CG,
		),
	)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/search/?page=")
			append(page.toString())
			when {
				!filter.query.isNullOrEmpty() -> {
					append("&key=")
					append(filter.query.urlEncoded())
				}

				else -> {

					if (filter.tags.isNotEmpty()) {
						append("&key=")
						filter.tags.joinTo(this, separator = ",") { it.key }
					}

					var types = "&m=1&d=1&w=1&i=1&a=1&g=1"
					if (filter.types.isNotEmpty()) {
						types = "&m=0&d=0&w=0&i=0&a=0&g=0"
						filter.types.forEach {
							when (it) {
								ContentType.MANGA -> types = types.replace("&m=0", "&m=1")
								ContentType.DOUJINSHI -> types = types.replace("&d=0", "&d=1")
								ContentType.COMICS -> types = types.replace("&w=0", "&w=1")
								ContentType.IMAGE_SET -> types = types.replace("&i=0", "&i=1")
								ContentType.ARTIST_CG -> types = types.replace("&a=0", "&a=1")
								ContentType.GAME_CG -> types = types.replace("&g=0", "&g=1")
								else -> {}
							}
						}
					}
					append(types)


					var lang = "&en=1&jp=1&es=1&fr=1&kr=1&de=1&ru=1"
					filter.locale?.let {
						lang = "&en=0&jp=0&es=0&fr=0&kr=0&de=0&ru=0"
						lang = lang.replace("${it.language}=0", "${it.language}=1")
					}
					append(lang)

					when (order) {
						SortOrder.UPDATED -> append("&lt=1&pp=0")
						SortOrder.POPULARITY -> append("&lt=0&pp=1")
						SortOrder.RATING -> append("&lt=0&pp=0")
						else -> append("&lt=1&pp=0")
					}
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
	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		return coroutineScope {
			(1..3).map { page ->
				async { fetchTagsPage(page) }
			}
		}.awaitAll().flattenTo(ArraySet(360))
	}

	private suspend fun fetchTagsPage(page: Int): Set<MangaTag> {
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

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		manga.copy(
			tags = doc.body().select("li:contains(Tags) a.tag").mapNotNullToSet {
				val href = it.attr("href").substringAfterLast("tag/").substringBeforeLast('/')
				MangaTag(
					key = href,
					title = it.ownText().toTitleCase(sourceLocale),
					source = source,
				)
			},
			author = doc.selectFirst("li:contains(Artists) a.tag")?.ownTextOrNull(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = manga.url,
					scanlator = null,
					uploadDate = 0,
					branch = doc.selectFirst("li:contains(Language) a.tag")?.ownTextOrNull()?.toTitleCase(sourceLocale),
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
		val totalPages = doc.selectFirstOrThrow(".pages").text().replace("Pages: ", "").toInt()
		val baseImg = doc.requireElementById("append_thumbs").selectFirstOrThrow("img")
		val baseUrl = baseImg.selectFirstParentOrThrow("a").attrAsRelativeUrl("href").replace("/1/", "/\$/")
		val baseThumbUrl = baseImg.src()?.replace("/1t.", "/\$t.")
		val pages = ArrayList<MangaPage>(totalPages)
		repeat(totalPages) { i ->
			val url = baseUrl.replace("\$", (i + 1).toString())
			pages.add(
				MangaPage(
					id = generateUid(url),
					url = url,
					preview = baseThumbUrl?.replace("\$", (i + 1).toString()),
					source = source,
				),
			)
		}
		return pages
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		val img = doc.body().requireElementById("gimg")
		return img.requireSrc()
	}
}
