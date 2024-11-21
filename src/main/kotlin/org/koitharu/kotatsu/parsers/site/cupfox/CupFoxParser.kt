package org.koitharu.kotatsu.parsers.site.cupfox

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class CupFoxParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 24,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.POPULARITY,
		SortOrder.UPDATED,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {
				!filter.query.isNullOrEmpty() -> {
					append("/search/")
					append(filter.query.urlEncoded())
					append('/')
					append(page)
				}

				else -> {
					append("/category/")

					when (order) {
						SortOrder.POPULARITY -> append("order/hits/")
						SortOrder.UPDATED -> append("order/addtime/")
						else -> append("order/addtime/")
					}

					filter.states.oneOrThrowIfMany()?.let {
						append(
							when (it) {
								MangaState.ONGOING -> "finish/1/"
								MangaState.FINISHED -> "finish/2/"
								else -> ""
							},
						)
					}

					if (filter.tags.isNotEmpty()) {
						filter.tags.oneOrThrowIfMany()?.let {
							append("tags/")
							append(it.key)
							append('/')
						}
					}

					append("page/")
					append(page)
				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open val selectMangas =
		"ul.row li, ul.stui-vodlist li, ul.clearfix li.dm-list, div.vod-list ul.row li, ul.ewave-vodlist li"
	protected open val selectMangasCover =
		"div.img-wrapper, div.stui-vodlist__thumb, a.stui-vodlist__thumb, div.ewave-vodlist__thumb, img.dm-thumb"

	private fun parseMangaList(doc: Document): List<Manga> {
		return doc.select(selectMangas).map { li ->
			val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				title = li.selectFirst("h3, h4, p.dm-bn")?.text().orEmpty(),
				altTitle = null,
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = li.selectFirst(selectMangasCover)
					?.src().orEmpty(),
				tags = setOf(),
				state = null,
				author = null,
				source = source,
			)
		}
	}

	protected open val selectMangaDetailsAltTitle =
		"div.info span:contains(Autres noms), div.info span:contains(Biệt danh)"
	protected open val selectMangaDetailsTags =
		"div.info span a[href*=tags], p.data a[href*=tags], div.book-main-right p.info-text a[href*=tags]"
	protected open val selectMangaDetailsAuthor =
		"div.info span:contains(Auteur(s)), div.info span:contains(Tác giả), p.data span:contains(Auteur(s)), p.data span:contains(Autor), p.data span:contains(作者), div.book-main-right div.book-info:contains(作者) .info-text"
	protected open val selectMangaDescription =
		"div.vod-list:contains(Résumé) div.more-box, div.stui-pannel__head:contains(Résumé), div.book-desc div.info-text, div.info div.text:contains(Giới thiệu), #desc"
	protected open val selectMangaChapters =
		"div.episode-box ul li, ul.stui-content__playlist li a, ul.cnxh-ul li a, ul.ewave-content__playlist li a"

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		return manga.copy(
			altTitle = doc.selectFirst(selectMangaDetailsAltTitle)?.text()?.substringAfter("："),
			state = null,
			tags = doc.select(selectMangaDetailsTags).mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			},
			author = doc.selectFirst(selectMangaDetailsAuthor)?.text()?.substringAfter("："),
			description = doc.selectFirst(selectMangaDescription)
				?.html(),
			chapters = doc.select(selectMangaChapters)
				.mapChapters { i, li ->
					val a = li.selectFirstOrThrow("a")
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = 0L,
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val doc = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select("div.vod-list div.more-box li, ul.stui-vodlist__bd li, ul.about-yxul li, ul.ewave-vodlist__bd li")
			.map { li ->
				val href = li.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					title = li.selectFirst("h3, h4, p.dm-bn")?.text().orEmpty(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = false,
					coverUrl = li.selectFirst(selectMangasCover)?.src().orEmpty(),
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	protected open val selectPages = "div.more-box li img, ul.main li img"

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		return doc.select(selectPages).map { img ->
			val url = img.requireSrc()
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	protected open val selectAvailableTags = "div.swiper-wrapper a[href*=tags], ul.stui-screen__list li a[href*=tags]"

	protected open suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/category/").parseHtml()
		return doc.select(selectAvailableTags)
			.mapToSet { a ->
				MangaTag(
					key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
					title = a.text().toTitleCase(),
					source = source,
				)
			}
	}
}
