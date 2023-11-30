package org.koitharu.kotatsu.parsers.site.fr

import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("FMTEAM", "FmTeam", "fr")
internal class FmTeam(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaSource.FMTEAM, 0) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)
	override val availableStates: Set<MangaState> = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED)
	override val configKeyDomain = ConfigKey.Domain("fmteam.fr")

	override suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		if (page > 1) {
			return emptyList()
		}
		var foundTag = true
		var foundState = true

		val manga = ArrayList<Manga>()

		when (filter) {
			is MangaListFilter.Search -> {
				val jsonManga = webClient.httpGet("https://$domain/api/search/${filter.query.urlEncoded()}").parseJson()
					.getJSONArray("comics")
				for (i in 0 until jsonManga.length()) {
					val j = jsonManga.getJSONObject(i)
					val href = "/api" + j.getString("url")
					manga.add(addManga(href, j))
				}
			}

			is MangaListFilter.Advanced -> {
				val jsonManga = webClient.httpGet("https://$domain/api/comics").parseJson().getJSONArray("comics")
				for (i in 0 until jsonManga.length()) {

					val j = jsonManga.getJSONObject(i)
					val href = "/api" + j.getString("url")

					if (filter.tags.isNotEmpty() && filter.states.isEmpty()) {
						val a = j.getJSONArray("genres").toString()
						foundTag = false
						filter.tags.forEach {
							if (a.contains(it.key, ignoreCase = true)) {
								foundTag = true
							}
						}
					}

					if (filter.states.isNotEmpty()) {
						val a = j.getString("status")
						foundState = false
						filter.states.oneOrThrowIfMany()?.let {
							if (a.contains(
									when (it) {
										MangaState.ONGOING -> "En cours"
										MangaState.FINISHED -> "Terminé"
										else -> ""
									},
									ignoreCase = true,
								)
							) {
								foundState = true
							}
						}

					}

					if (foundState && foundTag) {
						manga.add(addManga(href, j))
					}
				}
			}

			null -> {
				val jsonManga = webClient.httpGet("https://$domain/api/comics").parseJson().getJSONArray("comics")
				for (i in 0 until jsonManga.length()) {
					val j = jsonManga.getJSONObject(i)
					val href = "/api" + j.getString("url")
					manga.add(
						addManga(href, j),
					)
				}
			}
		}

		return manga
	}

	private fun addManga(href: String, j: JSONObject): Manga {
		return Manga(
			id = generateUid(href),
			url = href,
			publicUrl = href.toAbsoluteUrl(domain),
			coverUrl = j.getString("thumbnail"),
			title = j.getString("title"),
			description = j.getString("description"),
			altTitle = j.getJSONArray("alt_titles").toString()
				.replace("[\"", "")
				.replace("\"]", "")
				.replace("\",\"", " , "),
			rating = j.getString("rating").toFloatOrNull()?.div(10f)
				?: RATING_UNKNOWN,
			tags = emptySet(),
			author = j.getString("author"),
			state = when (j.getString("status").lowercase()) {
				"en cours" -> MangaState.ONGOING
				"terminé" -> MangaState.FINISHED
				else -> null
			},
			source = source,
			isNsfw = when (j.getString("adult").toInt()) {
				0 -> false
				1 -> true
				else -> true
			},
		)
	}


	override suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(fullUrl).parseJson().getJSONObject("comic")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
		val chapters = JSONArray(json.getJSONArray("chapters").toJSONList().reversed())

		manga.copy(
			tags = json.getJSONArray("genres").toJSONList().mapNotNullToSet {
				MangaTag(
					key = it.getString("slug"),
					title = it.getString("name"),
					source = source,
				)
			},
			chapters = chapters.mapJSONIndexed { i, j ->
				val url = "/api" + j.getString("url").toRelativeUrl(domain)
				val name = j.getString("full_title")
				val date = j.getStringOrNull("updated_at")
				MangaChapter(
					id = generateUid(url),
					name = name,
					number = i + 1,
					url = url,
					scanlator = null,
					uploadDate = dateFormat.tryParse(date),
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val fullUrl = chapter.url.toAbsoluteUrl(domain)
		val jsonPages = webClient.httpGet(fullUrl).parseJson().getJSONObject("chapter").getJSONArray("pages").toString()
		val pages = jsonPages.replace("[", "").replace("]", "")
			.replace("\\", "").split("\",\"").drop(1)
		return pages.map { url ->
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}
}
