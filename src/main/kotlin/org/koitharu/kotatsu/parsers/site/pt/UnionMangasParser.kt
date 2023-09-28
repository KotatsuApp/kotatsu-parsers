package org.koitharu.kotatsu.parsers.site.pt

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("UNION_MANGAS", "Union Mangás", "pt")
class UnionMangasParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.UNION_MANGAS, 40) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("guimah.com")

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (!query.isNullOrEmpty()) {
			return if (page == searchPaginator.firstPage) {
				search(query)
			} else {
				emptyList()
			}
		}
		val tag = tags.oneOrThrowIfMany()
		val url = urlBuilder()
			.addPathSegment("lista-mangas")
			.addPathSegment(
				when {
					tag != null -> tag.key
					sortOrder == SortOrder.ALPHABETICAL -> "a-z"
					else -> "visualizacoes"
				},
			).addPathSegment(page.toString())
		val doc = webClient.httpGet(url.build()).parseHtml()
		val root = doc.selectFirstOrThrow("div.tamanho-bloco-perfil")
		return root.select(".lista-perfil-mangas-novos").map { div ->
			val a = div.selectFirstOrThrow("a")
			val img = div.selectFirstOrThrow("img")
			val href = a.attrAsRelativeUrl("href")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = a.attrAsAbsoluteUrl("href"),
				title = div.selectLastOrThrow("a").text(),
				coverUrl = img.attrAsAbsoluteUrl("src"),
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = div.selectLast("div")?.ownText(),
				state = null,
				author = null,
				isNsfw = false,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val root = if (doc.selectFirst(".perfil-d-manga") == null) {
			doc.selectFirstOrThrow(".perfil-p-manga")
		} else {
			doc.selectFirstOrThrow(".perfil-d-manga")
		}
		val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
		return manga.copy(
			rating = root.select("h2")
				.find { it.ownText().startsWith('#') }
				?.ownText()?.drop(1)?.toFloatOrNull()?.div(10f) ?: manga.rating,
			largeCoverUrl = root.selectFirst("img.img-thumbnail")?.attrAsAbsoluteUrlOrNull("src"),
			description = root.selectFirst(".panel-default")?.selectFirst(".panel-body")?.html(),
			author = root.tableValue("Autor")?.ownText(),
			altTitle = root.tableValue("Título(s) Alternativo(s)")?.ownText(),
			state = when (root.tableValue("Status")?.selectLast(".label")?.text()) {
				"Completo" -> MangaState.FINISHED
				"Ativo" -> MangaState.ONGOING
				else -> null
			},
			tags = root.tableValue("Gênero(s)")?.select("a")?.mapToSet {
				it.toMangaTag()
			} ?: manga.tags,
			isNsfw = root.selectFirst(".alert-danger")?.html()?.contains("18 anos") == true,
			chapters = root.select("div.row.capitulos").mapChapters(reversed = true) { i, div ->
				val a = div.selectFirstOrThrow("a")
				val href = a.attrAsRelativeUrl("href")
				val title = a.text()
				MangaChapter(
					id = generateUid(href),
					name = title,
					number = i + 1,
					url = href,
					scanlator = div.selectLast("a")?.text()?.takeUnless { it == title },
					uploadDate = dateFormat.tryParse(
						a.nextElementSibling()?.text()?.removeSurrounding("(", ")"),
					),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().selectFirstOrThrow("article")
		return root.selectOrThrow("img[pag]").mapNotNull { img ->
			val href = img.attrAsRelativeUrl("src")
			if (href.startsWith("/images/banner")) {
				return@mapNotNull null
			}
			MangaPage(
				id = generateUid(href),
				url = href,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet(urlBuilder().addPathSegment("lista-mangas").build()).parseHtml()
		val ul = doc.body().selectFirstOrThrow(".nav-tabs").selectFirstOrThrow("ul.dropdown-menu")
		return ul.select("li").mapToSet { li ->
			li.selectFirstOrThrow("a").toMangaTag()
		}
	}

	private suspend fun search(query: String): List<Manga> {
		val domain = domain
		val json = webClient.httpGet(
			urlBuilder()
				.addPathSegments("assets/busca.php")
				.addQueryParameter("nomeManga", query)
				.build(),
		).parseJson()
		return json.getJSONArray("items").mapJSON { jo ->
			val href = "/perfil/" + jo.getString("url")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				title = jo.getString("titulo"),
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				author = jo.getStringOrNull("autor"),
				coverUrl = jo.getString("imagem"),
				state = null,
				isNsfw = false,
				altTitle = null,
				source = source,
			)
		}
	}

	private fun Element.tableValue(title: String): Element? {
		return select("h4.media-heading")
			.find { it.selectFirst("label.subtit-manga")?.text()?.contains(title, ignoreCase = true) == true }
	}

	private fun Element.toMangaTag() = MangaTag(
		title = text().toTitleCase(sourceLocale),
		key = attr("href").removeSuffix('/').substringAfterLast('/'),
		source = source,
	)
}
