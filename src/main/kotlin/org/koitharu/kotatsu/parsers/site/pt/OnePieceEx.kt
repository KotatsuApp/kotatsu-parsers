package org.koitharu.kotatsu.parsers.site.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@Broken
@MangaSourceParser("ONEPIECEEX", "OnePieceEx", "pt")
internal class OnePieceEx(context: MangaLoaderContext) : SinglePageMangaParser(context, MangaParserSource.ONEPIECEEX) {

	override val configKeyDomain = ConfigKey.Domain("onepieceex.net")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		return listOf(
			Manga(
				id = generateUid("https://$domain/mangas/leitor/"),
				url = "https://$domain/mangas/leitor/",
				publicUrl = "https://$domain/mangas/leitor/",
				title = "One Piece",
				coverUrl = "https://$domain/mangareader/sbs/capa/preview/Volume_1.jpg",
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = "Um romance marítimo pelo \"One Piece\"!!! Estamos na Grande " +
					"Era dos Piratas. Nela, muitos piratas lutam pelo tesouro deixado pelo " +
					"lendário Rei dos Piratas G. Roger, o \"One Piece\". Luffy, um garoto " +
					"que almeja ser pirata, embarca numa jornada com o sonho de se tornar " +
					"o Rei dos Piratas!!! (Fonte: MANGA Plus)",
				state = MangaState.ONGOING,
				author = "Eiichiro Oda",
				isNsfw = false,
				source = source,
			),

			Manga(
				id = generateUid("https://$domain/sbs/"),
				url = "https://$domain/sbs/",
				publicUrl = "https://$domain/sbs/",
				title = "One Piece",
				coverUrl = "https://$domain/mangareader/sbs/capa/preview/nao.jpg",
				altTitle = null,
				rating = RATING_UNKNOWN,
				tags = emptySet(),
				description = "O SBS é uma coluna especial encontrada na maioria dos " +
					"tankobons da coleção, começando a partir do volume 4. É geralmente " +
					"formatada como uma coluna direta de perguntas e respostas, com o " +
					"Eiichiro Oda respondendo as cartas de fãs sobre uma grande variedade " +
					"de assuntos. (Fonte: One Piece Wiki)",
				state = null,
				author = "Eiichiro Oda",
				isNsfw = false,
				source = source,
			),
		)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		if (manga.url.endsWith("/leitor/")) {
			val chap =
				webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml().requireElementById("capitulosLista")
					.select("optgroup option")
			return manga.copy(
				chapters = chap.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("value")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source,
					)
				},
			)
		} else {
			val chap =
				webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
					.requireElementById("post").select("a.bnt-lista-horizontal")
			return manga.copy(
				chapters = chap.mapChapters(reversed = true) { i, a ->
					val href = a.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(href),
						name = a.text(),
						number = i + 1f,
						volume = 0,
						url = href,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source,
					)
				},
			)
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val images = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
			.selectFirstOrThrow("script:containsData(paginasLista)").data()
			.substringAfter("paginasLista = ")
			.substringBefore(";").split(":").drop(1)
		val pages = ArrayList<MangaPage>(images.size)
		for (image in images) {
			val url = "https://$domain/" + image.replace("\\u00e9", "é")
				.replace("\\", "").replace("\"", "")
				.substringBefore(",")
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
