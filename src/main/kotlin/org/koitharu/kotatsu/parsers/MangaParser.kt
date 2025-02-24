package org.koitharu.kotatsu.parsers

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import java.util.*

public interface MangaParser {

	public val source: MangaParserSource

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	public val availableSortOrders: Set<SortOrder>

	public val searchQueryCapabilities: MangaSearchQueryCapabilities

	public val config: MangaSourceConfig

	public val domain: String

	public suspend fun queryManga(searchQuery: MangaSearchQuery): List<Manga>

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
}
