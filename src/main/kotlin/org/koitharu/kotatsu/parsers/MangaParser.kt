package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.OkHttpWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.FaviconParser
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

abstract class MangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi val context: MangaLoaderContext,
	val source: MangaSource,
) {

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	abstract val sortOrders: Set<SortOrder>

	val config by lazy { context.getConfig(source) }

	open val sourceLocale: Locale
		get() = source.locale?.let { Locale(it) } ?: Locale.ROOT

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [getDomain] instead.
	 */
	@InternalParsersApi
	abstract val configKeyDomain: ConfigKey.Domain

	open val headers: Headers? = null

	/**
	 * Used as fallback if value of `sortOrder` passed to [getList] is null
	 */
	protected open val defaultSortOrder: SortOrder
		get() {
			val supported = sortOrders
			return SortOrder.values().first { it in supported }
		}

	@JvmField
	protected val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param query search query, may be null or empty if no search needed
	 * @param tags genres for filtering, values from [getTags] and [Manga.tags]. May be null or empty
	 * @param sortOrder one of [sortOrders] or null for default value
	 */
	@JvmSynthetic
	@InternalParsersApi
	abstract suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga>

	/**
	 * Parse list of manga with search by text query
	 *
	 * @param offset starting from 0 and used for pagination.
	 * @param query search query
	 */
	open suspend fun getList(offset: Int, query: String): List<Manga> {
		return getList(offset, query, null, defaultSortOrder)
	}

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param tags genres for filtering, values from [getTags] and [Manga.tags]. May be null or empty
	 * @param sortOrder one of [sortOrders] or null for default value
	 */
	open suspend fun getList(offset: Int, tags: Set<MangaTag>?, sortOrder: SortOrder?): List<Manga> {
		return getList(offset, null, tags, sortOrder ?: defaultSortOrder)
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
	abstract suspend fun getTags(): Set<MangaTag>

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
}
