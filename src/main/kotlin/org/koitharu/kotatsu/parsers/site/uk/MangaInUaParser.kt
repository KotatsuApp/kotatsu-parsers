package org.koitharu.kotatsu.parsers.site.uk

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

private const val DEF_BRANCH_NAME = "Основний переклад"

@MangaSourceParser("MANGAINUA", "MANGA/in/UA", "uk")
internal class MangaInUaParser(context: MangaLoaderContext) : PagedMangaParser(
	context = context,
	source = MangaParserSource.MANGAINUA,
	pageSize = 24,
	searchPageSize = 10,
) {

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.UPDATED)

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("manga.in.ua")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	private val userHashRegex by lazy {
		Regex("site_login_hash\\s*=\\s*\'([^\']+)\'", RegexOption.IGNORE_CASE)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append("/index.php?do=search&subaction=search&search_start=$page&full_search=1&story=${filter.query}&titleonly=3")
				}

				else -> {

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("${it.key}/page/$page")
						}
					} else {
						append("/mangas/page/$page")
					}
				}
			}
		}

		val doc = webClient.httpGet(url).parseHtml()
		val container = doc.body().requireElementById("site-content")
		val items = container.select("div.col-6")
		return items.mapNotNull { item ->
			val href = item.selectFirst("a")?.attrAsRelativeUrl("href") ?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h3.card__title")?.text() ?: return@mapNotNull null,
				coverUrl = item.selectFirst("header.card__cover")?.selectFirst("img")?.run {
					attrAsAbsoluteUrlOrNull("data-src") ?: attrAsAbsoluteUrlOrNull("src")
				}.orEmpty(),
				altTitle = null,
				author = null,
				rating = item.selectFirst("div.card__short-rate--num")?.text()?.toFloatOrNull()?.div(10F)
					?: RATING_UNKNOWN,
				url = href,
				isNsfw = item.selectFirst("ul.card__list")?.select("li")?.lastOrNull()?.text() == "18+",
				tags = runCatching {
					item.selectFirst("div.card__category")?.select("a")?.mapToSet {
						MangaTag(
							title = it.ownText(),
							key = it.attrOrThrow("href").removeSuffix("/"),
							source = source,
						)
					}
				}.getOrNull().orEmpty(),
				state = null,
				publicUrl = href.toAbsoluteUrl(container.host ?: domain),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = doc.body().requireElementById("site-content")
		val linkToComics = root.requireElementById("linkstocomics")
		val userHash = doc.select("script").firstNotNullOf { script ->
			userHashRegex.find(script.html())?.groupValues?.getOrNull(1)
		}
		val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
		val chaptersDoc = webClient.httpPost(
			"https://$domain/engine/ajax/controller.php?mod=load_chapters",
			mapOf(
				"action" to "show",
				"news_id" to linkToComics.attrOrThrow("data-news_id"),
				"news_category" to linkToComics.attrOrThrow("data-news_category"),
				"this_link" to "",
				"user_hash" to userHash,
			),
		).parseHtml()
		val chapterNodes = chaptersDoc.select(".ltcitems")
		var prevChapterName: String? = null
		var i = 0
		return manga.copy(
			description = root.selectFirst("div.item__full-description")?.text(),
			largeCoverUrl = root.selectFirst("div.item__full-sidebar--poster")?.selectFirst("img")
				?.attrAsAbsoluteUrlOrNull("src"),
			chapters = chapterNodes.mapChapters { _, item ->
				val href = item.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: return@mapChapters null
				val isAlternative = item.styleValueOrNull("background") != null
				val name = item.selectFirst("a")?.text().orEmpty()
				if (!isAlternative) i++
				MangaChapter(
					id = generateUid(href),
					name = if (isAlternative) {
						prevChapterName ?: return@mapChapters null
					} else {
						prevChapterName = name
						name
					},
					number = i.toFloat(),
					volume = 0,
					url = href,
					scanlator = null,
					branch = if (isAlternative) {
						name.substringAfterLast(':').trim()
					} else {
						DEF_BRANCH_NAME
					},
					uploadDate = dateFormat.tryParse(item.selectFirst("div.ltcright")?.text()),
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val userHash = doc.select("script").firstNotNullOf { script ->
			userHashRegex.find(script.html())?.groupValues?.getOrNull(1)
		}
		val ajaxUrl = urlBuilder().addPathSegment("engine").addPathSegment("ajax").addPathSegment("controller.php")
			.addEncodedQueryParameter("mod", "load_chapters_image")
			.addQueryParameter("news_id", doc.requireElementById("comics").attrOrThrow("data-news_id"))
			.addEncodedQueryParameter("action", "show").addQueryParameter("user_hash", userHash).build()
		val root = webClient.httpGet(ajaxUrl).parseHtml().root()
		return root.select("li").map { ul ->
			val img = ul.selectFirstOrThrow("img")
			val url = img.attrAsAbsoluteUrl("data-src")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val domain = domain
		val doc = webClient.httpGet("https://$domain/mangas").parseHtml()
		val root = doc.body().requireElementById("menu_1").selectFirstOrThrow("div.menu__wrapper")
		return root.select("li").mapNotNullToSet { li ->
			val a = li.selectFirst("a") ?: return@mapNotNullToSet null
			MangaTag(
				title = a.ownText(),
				key = a.attrOrThrow("href").removeSuffix("/"),
				source = source,
			)
		}
	}
}
