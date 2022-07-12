package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.FaviconParser
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

abstract class MangaParser @InternalParsersApi constructor(val source: MangaSource) {

	protected abstract val context: MangaLoaderContext

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	abstract val sortOrders: Set<SortOrder>

	val config by lazy { context.getConfig(source) }

	val sourceLocale: Locale?
		get() = source.locale?.let { Locale(it) }

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [getDomain] instead.
	 */
	protected abstract val configKeyDomain: ConfigKey.Domain

	/**
	 * Used as fallback if value of `sortOrder` passed to [getList] is null
	 */
	protected open val defaultSortOrder: SortOrder
		get() {
			val supported = sortOrders
			return SortOrder.values().first { it in supported }
		}

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
	suspend fun getList(offset: Int, query: String): List<Manga> {
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
	suspend fun getList(offset: Int, tags: Set<MangaTag>?, sortOrder: SortOrder?): List<Manga> {
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
	open suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(getDomain())

	/**
	 * Fetch available tags (genres) for source
	 */
	abstract suspend fun getTags(): Set<MangaTag>

	/**
	 * Returns direct link to the website favicon
	 */
	@Deprecated(
		message = "Use parseFavicons() to get multiple favicons with different size",
		replaceWith = ReplaceWith("parseFavicons()"),
	)
	open fun getFaviconUrl() = "https://${getDomain()}/favicon.ico"

	open suspend fun parseFavicons(): Favicons {
		return FaviconParser(context, getDomain()).parseFavicons()
	}

	@CallSuper
	open fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	/* Utils */

	fun getDomain(): String {
		return config[configKeyDomain]
	}

	fun getDomain(subdomain: String): String {
		val domain = getDomain()
		return subdomain + "." + domain.removePrefix("www.")
	}

	/**
	 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
	 * @param url must be relative url, without a domain
	 * @see [Manga.id]
	 * @see [MangaChapter.id]
	 * @see [MangaPage.id]
	 */
	@InternalParsersApi
	protected fun generateUid(url: String): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		url.forEach { c ->
			h = 31 * h + c.code
		}
		return h
	}

	/**
	 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
	 * @param id an internal identifier
	 * @see [Manga.id]
	 * @see [MangaChapter.id]
	 * @see [MangaPage.id]
	 */
	@InternalParsersApi
	protected fun generateUid(id: Long): Long {
		var h = 1125899906842597L
		source.name.forEach { c ->
			h = 31 * h + c.code
		}
		h = 31 * h + id
		return h
	}

	@InternalParsersApi
	protected fun parseFailed(message: String? = null): Nothing {
		throw ParseException(message, null)
	}

	@InternalParsersApi
	protected fun Set<MangaTag>?.oneOrThrowIfMany(): MangaTag? {
		return when {
			isNullOrEmpty() -> null
			size == 1 -> first()
			else -> throw IllegalArgumentException("Multiple genres are not supported by this source")
		}
	}
}