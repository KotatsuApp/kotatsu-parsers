package org.koitharu.kotatsu.parsers.core

import androidx.annotation.CallSuper
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.network.KtorWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.FaviconParser
import org.koitharu.kotatsu.parsers.util.LinkResolver
import org.koitharu.kotatsu.parsers.util.RelatedMangaFinder
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

@InternalParsersApi
public abstract class AbstractMangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi public val context: MangaLoaderContext,
	public final override val source: MangaParserSource,
) : MangaParser {

	public override val config: MangaSourceConfig by lazy { context.getConfig(source) }

	public open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	protected open val userAgentKey: ConfigKey.UserAgent = ConfigKey.UserAgent(context.getDefaultUserAgent())

	protected val sourceContentRating: ContentRating?
		get() = if (source.contentType == ContentType.HENTAI) {
			ContentRating.ADULT
		} else {
			null
		}

	final override val domain: String
		get() = config[configKeyDomain]

	@Deprecated("Override intercept() instead")
	override fun getRequestHeaders(): Headers = headersOf(
		"User-Agent" to listOf(config[userAgentKey]),
	)

	/**
	 * Used as fallback if value of `order` passed to [getList] is null
	 */
	public open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	@JvmField
	protected val webClient: WebClient = KtorWebClient(context.httpClient, source)

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
	override suspend fun resolveLink(resolver: LinkResolver, link: Url): Manga? = null

	override suspend fun intercept(
		sender: Sender,
		request: HttpRequestBuilder,
	): HttpClientCall = sender.execute(request)
}
