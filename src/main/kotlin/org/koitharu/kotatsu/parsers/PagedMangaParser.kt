package org.koitharu.kotatsu.parsers

import androidx.annotation.RestrictTo
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.Paginator

@InternalParsersApi
abstract class PagedMangaParser(
	context: MangaLoaderContext,
	source: MangaSource,
	@RestrictTo(RestrictTo.Scope.TESTS) @JvmField internal val pageSize: Int,
	searchPageSize: Int = pageSize,
) : MangaParser(context, source) {

	@JvmField
	protected val paginator = Paginator(pageSize)

	@JvmField
	protected val searchPaginator = Paginator(searchPageSize)

	override suspend fun getList(offset: Int, query: String): List<Manga> {
		return getList(searchPaginator, offset, query, null, defaultSortOrder)
	}

	override suspend fun getList(offset: Int, tags: Set<MangaTag>?, sortOrder: SortOrder?): List<Manga> {
		return getList(paginator, offset, null, tags, sortOrder ?: defaultSortOrder)
	}

	@InternalParsersApi
	@Deprecated("You should use getListPage for PagedMangaParser", level = DeprecationLevel.HIDDEN)
	final override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> = throw UnsupportedOperationException("You should use getListPage for PagedMangaParser")

	abstract suspend fun getListPage(page: Int, query: String?, tags: Set<MangaTag>?, sortOrder: SortOrder): List<Manga>

	private suspend fun getList(
		paginator: Paginator,
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		val page = paginator.getPage(offset)
		val list = getListPage(page, query, tags, sortOrder)
		paginator.onListReceived(offset, page, list.size)
		return list
	}
}
