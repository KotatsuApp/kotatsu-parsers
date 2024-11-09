package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy

public class LinkResolver internal constructor(
	private val context: MangaLoaderContext,
	public val link: HttpUrl,
) {

	private val source = suspendLazy(initializer = ::resolveSource)

	public suspend fun getSource(): MangaParserSource? = source.get()

	public suspend fun getManga(): Manga? {
		val parser = context.newParserInstance(source.get() ?: return null)
		return parser.resolveLink(this, link) ?: resolveManga(parser)
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

	internal suspend fun resolveManga(
		parser: MangaParser,
		url: String = link.toString().toRelativeUrl(link.host),
		id: Long = parser.generateUid(url),
		title: String = STUB_TITLE,
	): Manga? = resolveBySeed(
		parser,
		Manga(
			id = id,
			title = title,
			altTitle = null,
			url = url,
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
			source = parser.source,
		),
	)

	private suspend fun resolveBySeed(parser: MangaParser, s: Manga): Manga? {
		val seed = parser.getDetails(s)
		if (!parser.filterCapabilities.isSearchSupported) {
			return seed.takeUnless { it.chapters.isNullOrEmpty() }
		}
		val query = when {
			seed.title != STUB_TITLE && seed.title.isNotEmpty() -> seed.title
			!seed.altTitle.isNullOrEmpty() -> seed.altTitle
			!seed.author.isNullOrEmpty() -> seed.author
			else -> return seed // unfortunately we do not know a real manga title so unable to find it
		}
		val resolved = runCatchingCancellable {
			val order = if (SortOrder.RELEVANCE in parser.availableSortOrders) {
				SortOrder.RELEVANCE
			} else {
				parser.defaultSortOrder
			}
			val list = parser.getList(0, order, MangaListFilter(query = query))
			list.singleOrNull { manga -> isSameUrl(manga.publicUrl) }
		}.getOrNull()
		if (resolved == null) {
			return seed
		}
		return runCatchingCancellable {
			parser.getDetails(resolved)
		}.getOrElse {
			resolved.copy(
				chapters = seed.chapters ?: resolved.chapters,
				description = seed.description ?: resolved.description,
				author = seed.author ?: resolved.author,
				tags = seed.tags + resolved.tags,
				state = seed.state ?: resolved.state,
				coverUrl = seed.coverUrl.ifEmpty { resolved.coverUrl },
				largeCoverUrl = seed.largeCoverUrl ?: resolved.largeCoverUrl,
				altTitle = seed.altTitle ?: resolved.altTitle,
			)
		}
	}

	private fun isSameUrl(publicUrl: String): Boolean {
		if (publicUrl == link.toString()) {
			return true
		}
		val httpUrl = publicUrl.toHttpUrlOrNull() ?: return false
		return link.host == httpUrl.host
			&& link.encodedPath == httpUrl.encodedPath
	}

	private companion object {

		const val STUB_TITLE = "Unknown manga"
	}
}
