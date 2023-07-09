package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.host
import org.koitharu.kotatsu.parsers.util.mapNotNullToSet
import org.koitharu.kotatsu.parsers.util.parseFailed
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.removeSuffix
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlEncoded
import java.util.EnumSet

@MangaSourceParser("VERMANHWA", "Vermanhwa", "es")
internal class Vermanhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.VERMANHWA, "vermanhwa.es", 10) {

	override val isNsfwSource = true
	override val datePattern = "MMMM d, yyyy"

	override val sortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.NEWEST,
		SortOrder.ALPHABETICAL,
		SortOrder.RATING,
		SortOrder.POPULARITY,
		SortOrder.UPDATED,


		)

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val url = buildString {
			append("https://")
			append(domain)
			val pages = page + 1

			when {
				!query.isNullOrEmpty() -> {
					append("/page/")
					append(pages.toString())
					append("/?s=")
					append(query.urlEncoded())
					append("&post_type=wp-manga")
				}

				!tags.isNullOrEmpty() -> {
					append("/manga-genre/")
					for (tag in tags) {
						append(tag.key)
					}
					append("/page/")
					append(pages.toString())
				}

				else -> {

					append("/manga/")
					append("/page/")
					append(pages.toString())
					append("?m_orderby=")
					if (sortOrder == SortOrder.NEWEST) {
						append("new-manga")
					}
					if (sortOrder == SortOrder.ALPHABETICAL) {
						append("alphabet")
					}
					if (sortOrder == SortOrder.RATING) {
						append("rating")
					}
					if (sortOrder == SortOrder.POPULARITY) {
						append("views")
					}
					if (sortOrder == SortOrder.UPDATED) {
						append("latest")
					}

				}
			}
		}
		val doc = webClient.httpGet(url).parseHtml()
		return doc.select("div.row.c-tabs-item__content").ifEmpty {
			doc.select("div.page-item-detail.manga")
		}.map { div ->
			val href = div.selectFirst("a")?.attrAsRelativeUrlOrNull("href") ?: div.parseFailed("Link not found")
			val summary = div.selectFirst(".tab-summary") ?: div.selectFirst(".item-summary")
			Manga(
				id = generateUid(href),
				url = href,
				publicUrl = href.toAbsoluteUrl(div.host ?: domain),
				coverUrl = div.selectFirst("img")?.src().orEmpty(),
				title = (summary?.selectFirst("h3") ?: summary?.selectFirst("h4"))?.text().orEmpty(),
				altTitle = null,
				rating = div.selectFirst("span.total_votes")?.ownText()?.toFloatOrNull()?.div(5f) ?: -1f,
				tags = summary?.selectFirst(".mg_genres")?.select("a")?.mapNotNullToSet { a ->
					MangaTag(
						key = a.attr("href").removeSuffix('/').substringAfterLast('/'),
						title = a.text().ifEmpty { return@mapNotNullToSet null }.toTitleCase(),
						source = source,
					)
				}.orEmpty(),
				author = summary?.selectFirst(".mg_author")?.selectFirst("a")?.ownText(),
				state = when (summary?.selectFirst(".mg_status")?.selectFirst(".summary-content")?.ownText()?.trim()
					?.lowercase()) {
					"مستمرة", "En curso", "En Curso", "Ongoing", "OnGoing", "On going", "Ativo", "En Cours", "En cours", "Activo",
					"En cours \uD83D\uDFE2", "En cours de publication", "Đang tiến hành", "Em lançamento", "em lançamento", "Em Lançamento",
					"Онгоінг", "Publishing", "Devam Ediyor", "Em Andamento", "Em andamento", "In Corso", "Güncel", "Berjalan", "Продолжается", "Updating",
					"Lançando", "In Arrivo", "Emision", "En emision", "مستمر", "Curso", "En marcha", "Publicandose", "Publicando", "连载中",
					"Devam ediyor",
					-> MangaState.ONGOING

					"Completed", "Completo", "Complété", "Fini", "Achevé", "Terminé", "Terminé ⚫", "Tamamlandı", "Đã hoàn thành", "Hoàn Thành", "مكتملة",
					"Завершено", "Finished", "Finalizado", "Completata", "One-Shot", "Bitti", "Tamat", "Completado", "Concluído", "Concluido", "已完结", "Bitmiş",
					-> MangaState.FINISHED

					else -> null
				},
				source = source,
				isNsfw = isNsfwSource,
			)
		}
	}
}
