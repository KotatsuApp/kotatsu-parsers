package org.koitharu.kotatsu.parsers.site.gattsu

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

internal abstract class GattsuParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 20,
) : PagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
	)

	protected open val tagPrefix = "tag"

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			when {

				!filter.query.isNullOrEmpty() -> {
					append("/page/")
					append(page.toString())
					append("/?s=")
					append(filter.query.urlEncoded())
				}

				else -> {

					filter.tags.oneOrThrowIfMany()?.let {
						append("/$tagPrefix/")
						append(it.key)
					}

					append("/page/")
					append(page.toString())

				}
			}
		}
		return parseMangaList(webClient.httpGet(url).parseHtml())
	}

	protected open fun parseMangaList(doc: Document): List<Manga> {
		return doc.select("div.lista ul li, div.videos div.video").mapNotNull { li ->
			val a = li.selectFirstOrThrow("a")
			val href = a.attrAsAbsoluteUrl("href")
			if (!href.contains(domain)) {
				//Some sources include ads in manga lists
				return@mapNotNull null
			}
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href,
				title = li.selectLast(".thumb-titulo, .video-titulo")?.text().orEmpty(),
				coverUrl = li.selectFirst("img")?.src().orEmpty(),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = null,
				state = null,
				author = null,
				isNsfw = isNsfwSource,
				source = source,
			)
		}
	}

	protected open val tagUrl = "generos"

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/$tagUrl/").parseHtml()
		return doc.selectLastOrThrow(".meio-conteudo p, div.lista-tags ul").parseTags()
	}

	protected open fun Element.parseTags() = select("a").mapToSet {
		val key = it.attr("href").removeSuffix("/").substringAfterLast("/")
		val name = it.selectFirst(".tag-titulo")?.text() ?: key
		MangaTag(
			key = key,
			title = name,
			source = source,
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val urlChapter = doc.selectFirstOrThrow("ul.post-fotos li a, ul.paginaPostBotoes a").attrAsAbsoluteUrl("href")
		return manga.copy(
			description = doc.selectFirst("div.post-texto")?.html(),
			tags = doc.selectFirst(".post-itens li:contains(Tags), .paginaPostInfo li:contains(Categorias)")
				?.parseTags().orEmpty(),
			author = doc.selectFirst(".post-itens li:contains(Autor) a, .paginaPostInfo li:contains(Artista) a")
				?.text(),
			chapters = listOf(
				MangaChapter(
					id = manga.id,
					name = manga.title,
					number = 1f,
					volume = 0,
					url = urlChapter,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		val totalPages =
			doc.selectLastOrThrow("div.galeria-paginacao span").text().substringAfterLast("- ").substringBeforeLast(')')
				.toInt()
		val rawUrl = chapter.url.substringBeforeLast("=")
		return (1..totalPages).map {
			val url = "$rawUrl=$it"
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		val doc = webClient.httpGet(page.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.selectFirstOrThrow("div.galeria-foto img").requireSrc()
	}
}
