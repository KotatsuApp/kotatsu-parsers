package org.koitharu.kotatsu.parsers

import io.ktor.http.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.network.HttpInterceptor
import org.koitharu.kotatsu.parsers.util.LinkResolver
import org.koitharu.kotatsu.parsers.util.convertToMangaSearchQuery
import org.koitharu.kotatsu.parsers.util.toMangaListFilterCapabilities
import java.util.*

public interface MangaParser : HttpInterceptor {

	public val source: MangaParserSource

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	public val availableSortOrders: Set<SortOrder>

	public val searchQueryCapabilities: MangaSearchQueryCapabilities

	public val config: MangaSourceConfig

	public val authorizationProvider: MangaParserAuthProvider?
		get() = this as? MangaParserAuthProvider

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [domain] instead.
	 */
	public val configKeyDomain: ConfigKey.Domain

	public val domain: String

	public suspend fun getList(query: MangaSearchQuery): List<Manga>

	/**
	 * Parse details for [Manga]: chapters list, description, large cover, etc.
	 * Must return the same manga, may change any fields excepts id, url and source
	 * @see Manga.copy
	 */
	public suspend fun getDetails(manga: Manga): Manga

	/**
	 * Parse pages list for specified chapter.
	 * @see MangaPage for details
	 */
	public suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	/**
	 * Fetch direct link to the page image.
	 */
	public suspend fun getPageUrl(page: MangaPage): String

	public suspend fun getFilterOptions(): MangaListFilterOptions

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	public suspend fun getFavicons(): Favicons

	public fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>)

	public suspend fun getRelatedManga(seed: Manga): List<Manga>

	public fun getRequestHeaders(): Headers

	/**
	 * Return [Manga] object by web link to it
	 * @see [Manga.publicUrl]
	 */
	@InternalParsersApi
	public suspend fun resolveLink(resolver: LinkResolver, link: Url): Manga?

	@Deprecated("Use getList(query: MangaSearchQuery) instead")
	public suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return getList(convertToMangaSearchQuery(offset, order, filter))
	}

	@Deprecated("Please check searchQueryCapabilities")
	public val filterCapabilities: MangaListFilterCapabilities
		get() = searchQueryCapabilities.toMangaListFilterCapabilities()
}
