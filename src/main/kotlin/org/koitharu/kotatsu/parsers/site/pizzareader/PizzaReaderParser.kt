package org.koitharu.kotatsu.parsers.site.pizzareader

import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.SinglePageMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.asTypedList
import org.koitharu.kotatsu.parsers.util.json.getStringOrNull
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONToSet
import java.text.SimpleDateFormat
import java.util.*

internal abstract class PizzaReaderParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
) : SinglePageMangaParser(context, source) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.ALPHABETICAL)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED),
		availableContentRating = EnumSet.of(ContentRating.SAFE, ContentRating.ADULT),
	)

	@JvmField
	protected val ongoing: Set<String> = hashSetOf(
		"en cours",
		"in corso",
		"in corso (cadenza irregolare)",
		"in corso (irregolare)",
		"in corso (mensile)",
		"in corso (quindicinale)",
		"in corso (settimanale)",
		"In corso (bisettimanale)",
	)


	@JvmField
	protected val finished: Set<String> = hashSetOf(
		"terminé",
		"concluso",
		"completato",
	)

	@JvmField
	protected val paused: Set<String> = hashSetOf(
		"in pausa",
		"in corso (in pausa)",
	)

	@JvmField
	protected val abandoned: Set<String> = hashSetOf(
		"droppato",
	)


	protected open val ongoingFilter = "in corso"
	protected open val completedFilter = "concluso"
	protected open val hiatusFilter = "in pausa"
	protected open val abandonedFilter = "droppato"

	override suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga> {
		var foundTag = true
		var foundTagExclude = true
		var foundState = true
		var foundContentRating = true

		val manga = ArrayList<Manga>()

		when {
			!filter.query.isNullOrEmpty() -> {
				val jsonManga = webClient.httpGet("https://$domain/api/search/${filter.query.urlEncoded()}").parseJson()
					.getJSONArray("comics")
				for (i in 0 until jsonManga.length()) {
					val j = jsonManga.getJSONObject(i)
					val href = "/api" + j.getString("url")
					manga.add(addManga(href, j))
				}
			}

			else -> {
				val jsonManga = webClient.httpGet("https://$domain/api/comics").parseJson().getJSONArray("comics")
				for (i in 0 until jsonManga.length()) {

					val j = jsonManga.getJSONObject(i)
					val href = "/api" + j.getString("url")

					if (filter.tags.isNotEmpty()) {
						val a = j.getJSONArray("genres").toString()
						foundTag = false
						filter.tags.forEach {
							if (a.contains(it.key, ignoreCase = true)) {
								foundTag = true
							}
						}
					}

					if (filter.tagsExclude.isNotEmpty()) {
						val a = j.getJSONArray("genres").toString()
						foundTagExclude = false
						filter.tagsExclude.forEach {
							if (!a.contains(it.key, ignoreCase = true)) {
								foundTagExclude = true
							}
						}
					}

					if (filter.states.isNotEmpty()) {
						val a = j.getString("status")
						foundState = false
						filter.states.oneOrThrowIfMany()?.let {
							if (a.lowercase().contains(
									when (it) {
										MangaState.PAUSED -> hiatusFilter
										MangaState.ONGOING -> ongoingFilter
										MangaState.FINISHED -> completedFilter
										MangaState.ABANDONED -> abandonedFilter
										else -> ""
									},
									ignoreCase = true,
								)
							) {
								foundState = true
							}
						}

					}

					if (filter.contentRating.isNotEmpty()) {
						val a = j.getInt("adult")
						foundContentRating = false
						filter.contentRating.oneOrThrowIfMany()?.let {
							if (a == (
									when (it) {
										ContentRating.SAFE -> 0
										ContentRating.ADULT -> 1
										else -> 0
									}
									)
							) {
								foundContentRating = true
							}
						}

					}

					if (foundState && foundTag && foundTagExclude && foundContentRating) {
						manga.add(addManga(href, j))
					}
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
				in ongoing -> MangaState.ONGOING
				in finished -> MangaState.FINISHED
				in paused -> MangaState.PAUSED
				in abandoned -> MangaState.ABANDONED
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

	override suspend fun getDetails(manga: Manga): Manga = coroutineScope {
		val fullUrl = manga.url.toAbsoluteUrl(domain)
		val json = webClient.httpGet(fullUrl).parseJson().getJSONObject("comic")
		val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
		val chapters = JSONArray(json.getJSONArray("chapters").asTypedList<JSONObject>().reversed())

		manga.copy(
			tags = json.getJSONArray("genres").mapJSONToSet {
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
					number = i + 1f,
					volume = 0,
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
