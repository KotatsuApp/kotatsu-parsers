package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import okhttp3.Headers
import okhttp3.HttpUrl
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities
import org.koitharu.kotatsu.parsers.network.OkHttpWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@InternalParsersApi
public abstract class AbstractMangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi public val context: MangaLoaderContext,
	public override val source: MangaParserSource,
) : MangaParser {

	@Deprecated("Please check searchQueryCapabilities")
	public abstract val filterCapabilities: MangaListFilterCapabilities

	public override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = filterCapabilities.toMangaSearchQueryCapabilities()

	public override val config: MangaSourceConfig by lazy { context.getConfig(source) }

	public open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	protected val isNsfwSource: Boolean = source.contentType == ContentType.HENTAI

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [domain] instead.
	 */
	@InternalParsersApi
	public abstract val configKeyDomain: ConfigKey.Domain

	protected open val userAgentKey: ConfigKey.UserAgent = ConfigKey.UserAgent(context.getDefaultUserAgent())

	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.build()

	/**
	 * Used as fallback if value of `order` passed to [getList] is null
	 */
	public open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	override val domain: String
		get() = config[configKeyDomain]

	@JvmField
	protected val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Search list of manga by specified searchQuery
	 *
	 * @param searchQuery searchQuery
	 */
	public override suspend fun queryManga(searchQuery: MangaSearchQuery): List<Manga> {
		if (!searchQuery.skipValidation) {
			searchQueryCapabilities.validate(searchQuery)
		}

		return getList(searchQuery)
	}

	/**
	 * Search list of manga by specified searchQuery
	 *
	 * @param query searchQuery
	 */
	protected open suspend fun getList(query: MangaSearchQuery): List<Manga> = getList(
		offset = query.offset,
		order = query.order ?: defaultSortOrder,
		filter = convertToMangaListFilter(query),
	)

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param order one of [availableSortOrders] or [defaultSortOrder] for default value
	 * @param filter is a set of filter rules
	 *
	 * @deprecated New [getList] should be preferred.
	 */
	@Deprecated("New getList(query: MangaSearchQuery) method should be preferred")
	public abstract suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga>

	/**
	 * Fetch direct link to the page image.
	 */
	public override suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(domain)

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	public override suspend fun getFavicons(): Favicons {
		return FaviconParser(webClient, domain).parseFavicons()
	}

	@CallSuper
	public override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	public override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return RelatedMangaFinder(listOf(this)).invoke(seed)
	}

	/**
	 * Return [Manga] object by web link to it
	 * @see [Manga.publicUrl]
	 */
	internal open suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? = null

}
