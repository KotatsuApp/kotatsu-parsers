package org.koitharu.kotatsu.parsers

import androidx.annotation.RestrictTo
import org.koitharu.kotatsu.parsers.model.*
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

	final override suspend fun getList(offset: Int, filter: MangaListFilter?): List<Manga> {
		return getList(
			paginator = if (filter is MangaListFilter.Search) {
				searchPaginator
			} else {
				paginator
			},
			offset = offset,
			filter = filter,
		)
	}

	@InternalParsersApi
	@Deprecated("You should use getListPage for PagedMangaParser", level = DeprecationLevel.HIDDEN)
	final override suspend fun getList(
		offset: Int,
		query: String?,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> = throw UnsupportedOperationException("You should use getListPage for PagedMangaParser")

	open suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		tagsExclude: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> = throw NotImplementedError("Please implement getListPage(page, filter) instead")

	open suspend fun getListPage(page: Int, filter: MangaListFilter?): List<Manga> {
		return when (filter) {
			is MangaListFilter.Advanced -> getListPage(page, null, filter.tags, filter.tagsExclude, filter.sortOrder)
			is MangaListFilter.Search -> getListPage(page, filter.query, null, null, defaultSortOrder)
			null -> getListPage(page, null, null, null, defaultSortOrder)
		}
	}

	private suspend fun getList(
		paginator: Paginator,
		offset: Int,
		filter: MangaListFilter?,
	): List<Manga> {
		val page = paginator.getPage(offset)
		val list = getListPage(page, filter)
		paginator.onListReceived(offset, page, list.size)
		return list
	}
}
