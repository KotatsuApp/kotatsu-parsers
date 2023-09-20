package org.koitharu.kotatsu.parsers.site.pt

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("GOLDENMANGA", "Golden Manga", "pt")
internal class GoldenManga(context: MangaLoaderContext) : PagedMangaParser(context, MangaSource.GOLDENMANGA, 36) {

	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override val configKeyDomain = ConfigKey.Domain("goldenmanga.top")
	override val headers: Headers = Headers.Builder()
		.add("User-Agent", UserAgents.CHROME_MOBILE)
		.build()

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			append("/mangas")
			append("?pagina=")
			append(page.toString())
			if (!query.isNullOrEmpty()) {
				append("&search=")
				append(query.urlEncoded())
			}
			if (!tags.isNullOrEmpty()) {
				append("&genero=")
				for (tag in tags) {
					append(tag.key)
					append(",")
				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("section.row div.mangas")
			.map { div ->
				val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
				Manga(
					id = generateUid(href),
					title = div.selectFirstOrThrow("a h3").text(),
					altTitle = null,
					url = href,
					publicUrl = href.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					isNsfw = div.selectFirst("div.MangaAdulto") != null,
					coverUrl = div.selectFirstOrThrow("img").attrAsAbsoluteUrl("src"),
					tags = setOf(),
					state = null,
					author = null,
					source = source,
				)
			}
	}

	override suspend fun getTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/mangas").parseHtml()
		return doc.select("div.container a.btn.btn-warning ").mapNotNullToSet { a ->
			MangaTag(
				key = a.attr("href").substringAfterLast("="),
				title = a.text(),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val root = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val dateFormat = SimpleDateFormat("(dd/MM/yyyy)", Locale.ENGLISH)
		return manga.copy(
			altTitle = null,
			state = when (root.select("h5.cg_color")[3].select("a").text()) {
				"ativo", "Ativo" -> MangaState.ONGOING
				"Completo" -> MangaState.FINISHED
				else -> null
			},
			tags = root.select("h5.cg_color")[0].select("a").mapNotNullToSet { a ->

				if (a.text().isNullOrEmpty()) {
					return@mapNotNullToSet null
				} else {
					MangaTag(
						key = a.attr("href").substringAfterLast("="),
						title = a.text().toTitleCase(),
						source = source,
					)
				}
			},
			author = root.select("h5.cg_color a")[1].text(),
			description = root.getElementById("manga_capitulo_descricao")?.html(),
			chapters = root.requireElementById("capitulos").select("li")
				.mapChapters(reversed = true) { i, div ->
					val href = div.selectFirstOrThrow("a").attrAsRelativeUrl("href")
					val dateText = div.selectFirstOrThrow("div.col-sm-5 span").text()
					val name = div.selectFirstOrThrow("div.col-sm-5").text().substringBeforeLast("(")
					MangaChapter(
						id = generateUid(href),
						name = name,
						number = i,
						url = href,
						scanlator = null,
						uploadDate = dateFormat.tryParse(dateText),
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val doc = webClient.httpGet(fullUrl).parseHtml()
		val root = doc.body().requireElementById("capitulos_images")
		return root.select("img").map { img ->
			val url = img.src()?.toRelativeUrl(domain) ?: img.parseFailed("Image src not found")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
