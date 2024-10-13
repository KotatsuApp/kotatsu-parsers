package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.*

public class LinkResolver internal constructor(
	private val context: MangaLoaderContext,
	public val link: HttpUrl,
) {

	private val source = SuspendLazy(::resolveSource)

	public suspend fun getSource(): MangaParserSource? = source.get()

	public suspend fun getManga(): Manga? {
		val parser = context.newParserInstance(source.get() ?: return null)
		return parser.resolveLink(link) ?: parser.resolveLinkLongPath()
	}

	private suspend fun resolveSource(): MangaParserSource? = runInterruptible(Dispatchers.Default) {
		val domains = setOfNotNull(link.host, link.topPrivateDomain())
		for (s in MangaParserSource.entries) {
			val parser = context.newParserInstance(s)
			for (d in parser.configKeyDomain.presetValues) {
				if (d in domains) {
					return@runInterruptible s
				}
			}
		}
		null
	}

	private suspend fun MangaParser.resolveLinkLongPath(): Manga? {
		val stubTitle = link.pathSegments.lastOrNull().orEmpty()
		val seed = Manga(
			id = 0L,
			title = stubTitle,
			altTitle = null,
			url = link.toString().toRelativeUrl(link.host),
			publicUrl = link.toString(),
			rating = RATING_UNKNOWN,
			isNsfw = false,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			author = null,
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = source,
		).let { manga ->
			getDetails(manga)
		}
		val query = when {
			seed.title != stubTitle && seed.title.isNotEmpty() -> seed.title
			!seed.altTitle.isNullOrEmpty() -> seed.altTitle
			!seed.author.isNullOrEmpty() -> seed.author
			else -> return seed // unfortunately we do not know a real manga title so unable to find it
		}
		return runCatchingCancellable {
			val order = if (SortOrder.RELEVANCE in availableSortOrders) SortOrder.RELEVANCE else defaultSortOrder
			val list = getList(0, order, MangaListFilter(query = query))
			list.single { manga -> isSameUrl(manga.publicUrl) }
		}.getOrDefault(seed)
	}

	private fun isSameUrl(publicUrl: String): Boolean {
		if (publicUrl == link.toString()) {
			return true
		}
		val httpUrl = publicUrl.toHttpUrlOrNull() ?: return false
		return link.host == httpUrl.host
			&& link.encodedPath == httpUrl.encodedPath
	}
}
