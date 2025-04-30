package org.koitharu.kotatsu.parsers.site.mangadventure

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.*
import java.util.*

internal abstract class MangAdventureParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	domain: String,
	pageSize: Int = 25,
) : LegacyPagedMangaParser(context, source, pageSize) {

	override val configKeyDomain = ConfigKey.Domain(domain)

	override val userAgentKey = ConfigKey.UserAgent(UserAgents.KOTATSU)

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)

	override val defaultSortOrder = SortOrder.ALPHABETICAL

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.ABANDONED,
			MangaState.PAUSED,
		),
		availableContentRating = EnumSet.of(ContentRating.SAFE),
	)

	override suspend fun getListPage(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val url = apiUrl.apply {
			appendPathSegments("series")
			parameters.append("limit", pageSize.toString())
			parameters.append("page", page.toString())
		}

		filter.query?.let {
			url.parameters.append("title", filter.query)
		}

		url.parameters.append(
			"categories",
			buildString {
				filter.tags.joinTo(this, ",", postfix = ",") { it.key }
				filter.tagsExclude.joinTo(this, ",") { "-" + it.key }
			},
		)

		filter.states.oneOrThrowIfMany()?.let {
			when (it) {
				MangaState.ONGOING -> url.parameters.append("status", "ongoing")
				MangaState.FINISHED -> url.parameters.append("status", "completed")
				MangaState.ABANDONED -> url.parameters.append("status", "canceled")
				MangaState.PAUSED -> url.parameters.append("status", "hiatus")
				else -> url.parameters.append("status", "any")
			}
		}

		when (order) {
			SortOrder.ALPHABETICAL -> url.parameters.append("sort", "title")
			SortOrder.ALPHABETICAL_DESC -> url.parameters.append("sort", "-title")
			SortOrder.UPDATED -> url.parameters.append("sort", "-latest_upload")
			SortOrder.POPULARITY -> url.parameters.append("sort", "-views")
			else -> url.parameters.append("sort", "-latest_upload")
		}

		return runCatchingCancellable { getManga(url.get()) }.getOrElse {
			if (it is NotFoundException) emptyList() else throw it
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = apiUrl.appendPathSegments("series", manga.slug)
		val details = requireNotNull(url.get())
		val chapters = url.appendPathSegments("chapters", "date_format", "timestamp").get()
		val author = buildString {
			val authors = details.getJSONArray("authors")
			val artists = details.getJSONArray("artists")
			if (authors.length() > 0 && artists.length() > 0) {
				authors.joinTo(this, postfix = ", ")
				artists.joinTo(this)
			} else if (authors.length() > 0) {
				authors.joinTo(this)
			} else if (artists.length() > 0) {
				artists.joinTo(this)
			}
		}
		return manga.copy(
			description = details.getStringOrNull("description"),
			altTitles = details.getJSONArray("aliases").toStringSet(),
			authors = setOf(author),
			tags = details.getJSONArray("categories").mapTo(HashSet()) {
				val name = it as String
				MangaTag(name, name, source)
			},
			state = when (details.getString("status")) {
				"ongoing" -> MangaState.ONGOING
				"completed" -> MangaState.FINISHED
				"canceled" -> MangaState.ABANDONED
				"hiatus" -> MangaState.PAUSED
				else -> null
			},
			chapters = chapters.optJSONArray("results")?.asTypedList<JSONObject>()?.mapChapters { _, it ->
				MangaChapter(
					id = generateUid(it.getLong("id")),
					title = it.getStringOrNull("full_title"),
					number = it.getFloatOrDefault("number", 0f),
					volume = it.getIntOrDefault("volume", 0),
					url = it.getString("url"),
					scanlator = it.getJSONArray("groups").joinToString(),
					uploadDate = it.getString("published").toLong(),
					branch = null,
					source = source,
				)
			}.orEmpty(),
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = apiUrl.apply {
			appendPathSegments("chapters", chapter.id.toString(), "pages")
			parameters.append("track", "true")
		}
		return url.get().optJSONArray("results")?.mapJSON {
			MangaPage(
				id = generateUid(it.getLong("id")),
				url = it.getString("image"),
				preview = null,
				source = source,
			)
		} ?: emptyList()
	}

	override suspend fun getPageUrl(page: MangaPage) = page.url

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val url = apiUrl.apply {
			appendPathSegments("categories")
		}
		return url.get().optJSONArray("results")?.mapJSONToSet {
			val name = it.getString("name")
			MangaTag(name, name, source)
		} ?: emptySet()
	}

	// webp favicons are not supported
	override suspend fun getFavicons() =
		Favicons(listOf(Favicon("https://$domain/media/logo.png", 512, "")), domain)

	/* Get a list of manga from the given [JSONObject]. */
	protected fun getManga(json: JSONObject?): List<Manga> {
		return json?.optJSONArray("results")?.mapJSONNotNull {
			// exclude licensed series
			if (it.opt("chapters") == JSONObject.NULL)
				return@mapJSONNotNull null
			val path = it.getString("url")
			val publicUrl = urlBuilder().apply {
				appendPathSegments(path)
			}.buildString()
			Manga(
				id = generateUid(it.getString("slug")),
				title = it.getString("title"),
				altTitles = emptySet(),
				url = path,
				publicUrl = publicUrl,
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = it.getString("cover"),
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = source,
			)
		} ?: emptyList()
	}

	protected val apiUrl: URLBuilder
		get() = urlBuilder().apply {
			appendPathSegments("api", "v2")
		}

	// /reader/{slug}/
	private val Manga.slug: String
		get() = url.substring(8, url.length - 1)

	protected suspend fun URLBuilder.get() = webClient.httpGet(build()).parseJson()
}
