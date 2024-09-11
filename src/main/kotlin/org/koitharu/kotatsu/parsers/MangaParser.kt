package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.OkHttpWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.FaviconParser
import org.koitharu.kotatsu.parsers.util.RelatedMangaFinder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

abstract class MangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi val context: MangaLoaderContext,
	val source: MangaParserSource,
) {

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	abstract val availableSortOrders: Set<SortOrder>

	/**
	 * Supported [MangaState] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	open val availableStates: Set<MangaState>
		get() = emptySet()


	open val availableContentRating: Set<ContentRating>
		get() = emptySet()

	/**
	 * Whether parser supports filtering by more than one tag
	 */
	open val isMultipleTagsSupported: Boolean = true

	/**
	 * Whether parser supports tagsExclude field in filter
	 */
	open val isTagsExclusionSupported: Boolean = false

	/**
	 * Whether parser supports searching by string query using [MangaListFilter.Search]
	 */
	open val isSearchSupported: Boolean = true

	@Deprecated(
		message = "Use availableSortOrders instead",
		replaceWith = ReplaceWith("availableSortOrders"),
	)
	open val sortOrders: Set<SortOrder>
		get() = availableSortOrders

	val config by lazy { context.getConfig(source) }

	open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	val isNsfwSource = source.contentType == ContentType.HENTAI

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [domain] instead.
	 */
	@InternalParsersApi
	abstract val configKeyDomain: ConfigKey.Domain

	protected open val userAgentKey = ConfigKey.UserAgent(context.getDefaultUserAgent())

	open fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.build()

	/**
	 * Used as fallback if value of `sortOrder` passed to [getList] is null
	 */
	@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
	open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	@JvmField
	protected val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param query search query, may be null or empty if no search needed
	 * @param tags genres for filtering, values from [getAvailableTags] and [Manga.tags]. May be null or empty
	 * @param sortOrder one of [availableSortOrders] or null for default value
	 */
	@JvmSynthetic
	@InternalParsersApi
	@Deprecated(
		"Use getList with filter instead",
		replaceWith = ReplaceWith("getList(offset, filter)"),
	)
	open suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> = throw NotImplementedError("Please implement getList(offset, filter) instead")

	/**
	 * Parse list of manga with search by text query
	 *
	 * @param offset starting from 0 and used for pagination.
	 * @param query search query
	 */
	@Deprecated(
		"Use getList with filter instead",
		ReplaceWith(
			"getList(offset, MangaListFilter.Search(query))",
			"org.koitharu.kotatsu.parsers.model.MangaListFilter",
		),
	)
	open suspend fun getList(offset: Int, query: String): List<Manga> {
		return getList(offset, MangaListFilter.Search(query))
	}

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param tags genres for filtering, values from [getAvailableTags] and [Manga.tags]. May be null or empty
	 * @param sortOrder one of [availableSortOrders] or null for default value
	 */
	@Deprecated(
		"Use getList with filter instead",
		ReplaceWith(
			"getList(offset, MangaListFilter.Advanced(sortOrder, tags, null, emptySet()))",
			"org.koitharu.kotatsu.parsers.model.MangaListFilter",
		),
	)
	open suspend fun getList(
		offset: Int,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder?,
	): List<Manga> {
		return getList(
			offset,
			MangaListFilter.Advanced(
				sortOrder = sortOrder ?: defaultSortOrder,
				tags = tags.orEmpty(),
				tagsExclude = tagsExclude.orEmpty(),
				locale = null,
				states = emptySet(),
				contentRating = emptySet(),
			),
		)
	}

	@Suppress("DEPRECATION")
	open suspend fun getList(offset: Int, filter: MangaListFilter?): List<Manga> {
		return when (filter) {
			is MangaListFilter.Advanced -> getList(
				offset = offset,
				query = null,
				tags = filter.tags,
				tagsExclude = filter.tagsExclude,
				sortOrder = filter.sortOrder,
			)

			is MangaListFilter.Search -> getList(
				offset = offset,
				query = filter.query,
				tags = null,
				tagsExclude = null,
				sortOrder = defaultSortOrder,
			)

			null -> getList(
				offset = offset,
				query = null,
				tags = null,
				tagsExclude = null,
				sortOrder = defaultSortOrder,
			)
		}
	}

	/**
	 * Parse details for [Manga]: chapters list, description, large cover, etc.
	 * Must return the same manga, may change any fields excepts id, url and source
	 * @see Manga.copy
	 */
	abstract suspend fun getDetails(manga: Manga): Manga

	/**
	 * Parse pages list for specified chapter.
	 * @see MangaPage for details
	 */
	abstract suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	/**
	 * Fetch direct link to the page image.
	 */
	open suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(domain)

	/**
	 * Fetch available tags (genres) for source
	 */
	abstract suspend fun getAvailableTags(): Set<MangaTag>

	/**
	 * Fetch available locales for multilingual sources
	 */
	open suspend fun getAvailableLocales(): Set<Locale> = emptySet()

	@Deprecated(
		message = "Use getAvailableTags instead",
		replaceWith = ReplaceWith("getAvailableTags()"),
	)
	suspend fun getTags(): Set<MangaTag> = getAvailableTags()

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	open suspend fun getFavicons(): Favicons {
		return FaviconParser(webClient, domain).parseFavicons()
	}

	@CallSuper
	open fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	open suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return RelatedMangaFinder(listOf(this)).invoke(seed)
	}

	protected fun getParser(source: MangaParserSource) = if (this.source == source) {
		this
	} else {
		context.newParserInstance(source)
	}
}
