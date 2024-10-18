package org.koitharu.kotatsu.parsers.site.mangadventure

import okhttp3.HttpUrl
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.PagedMangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
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
) : PagedMangaParser(context, source, pageSize) {

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
		val url = apiUrl.addEncodedPathSegment("series")
			.addEncodedQueryParameter("limit", pageSize.toString())
			.addEncodedQueryParameter("page", page.toString())

		filter.query?.let {
			url.addQueryParameter("title", filter.query)
		}

		url.addQueryParameter(
			"categories",
			buildString {
				filter.tags.joinTo(this, ",", postfix = ",") { it.key }
				filter.tagsExclude.joinTo(this, ",") { "-" + it.key }
			},
		)

		filter.states.oneOrThrowIfMany()?.let {
			when (it) {
				MangaState.ONGOING -> url.addEncodedQueryParameter("status", "ongoing")
				MangaState.FINISHED -> url.addEncodedQueryParameter("status", "completed")
				MangaState.ABANDONED -> url.addEncodedQueryParameter("status", "canceled")
				MangaState.PAUSED -> url.addEncodedQueryParameter("status", "hiatus")
				else -> url.addEncodedQueryParameter("status", "any")
			}
		}

		when (order) {
			SortOrder.ALPHABETICAL -> url.addEncodedQueryParameter("sort", "title")
			SortOrder.ALPHABETICAL_DESC -> url.addEncodedQueryParameter("sort", "-title")
			SortOrder.UPDATED -> url.addEncodedQueryParameter("sort", "-latest_upload")
			SortOrder.POPULARITY -> url.addEncodedQueryParameter("sort", "-views")
			else -> url.addEncodedQueryParameter("sort", "-latest_upload")
		}

		return runCatchingCancellable { getManga(url.get()) }.getOrElse {
			if (it is NotFoundException) emptyList() else throw it
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = apiUrl.addEncodedPathSegment("series").addPathSegment(manga.slug)
		val details = requireNotNull(url.get())
		val chapters = url.addEncodedPathSegment("chapters")
			.addEncodedQueryParameter("date_format", "timestamp").get()
		return manga.copy(
			description = details.getStringOrNull("description"),
			altTitle = details.getJSONArray("aliases").joinToString(),
			author = buildString {
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
			},
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
			chapters = chapters?.optJSONArray("results")?.asTypedList<JSONObject>()?.mapChapters { _, it ->
				MangaChapter(
					id = generateUid(it.getLong("id")),
					name = it.getString("full_title"),
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
		val url = apiUrl.addEncodedPathSegment("chapters")
			.addEncodedPathSegment(chapter.id.toString())
			.addEncodedPathSegment("pages")
			.addEncodedQueryParameter("track", "true")
		return url.get()?.optJSONArray("results")?.mapJSON {
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
		val url = apiUrl.addEncodedPathSegment("categories")
		return url.get()?.optJSONArray("results")?.mapJSONToSet {
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
			val publicUrl = urlBuilder().addEncodedPathSegments(path).toString()
			Manga(
				id = generateUid(it.getString("slug")),
				title = it.getString("title"),
				altTitle = null,
				url = path,
				publicUrl = publicUrl,
				rating = RATING_UNKNOWN,
				isNsfw = false,
				coverUrl = it.getString("cover"),
				tags = emptySet(),
				state = null,
				author = null,
				source = source,
			)
		} ?: emptyList()
	}

	protected val apiUrl: HttpUrl.Builder
		get() = urlBuilder().addEncodedPathSegments("api/v2")

	// /reader/{slug}/
	private val Manga.slug: String
		get() = url.substring(8, url.length - 1)

	protected suspend fun HttpUrl.Builder.get() =
		webClient.httpGet(build()).body?.string()?.let(::JSONObject)
}
