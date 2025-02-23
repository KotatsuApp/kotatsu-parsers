package org.koitharu.kotatsu.parsers

import androidx.annotation.VisibleForTesting
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.SearchableField
import org.koitharu.kotatsu.parsers.util.Paginator
import org.koitharu.kotatsu.parsers.util.convertToMangaListFilter

@InternalParsersApi
public abstract class PagedMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
	@VisibleForTesting(otherwise = VisibleForTesting.PROTECTED) @JvmField public val pageSize: Int,
	searchPageSize: Int = pageSize,
) : MangaParser(context, source) {

	@JvmField
	protected val paginator: Paginator = Paginator(pageSize)

	@JvmField
	protected val searchPaginator: Paginator = Paginator(searchPageSize)

	final override suspend fun validatedMangaSearch(searchQuery: MangaSearchQuery): List<Manga> {
		var containTitleNameCriteria = false
		searchQuery.criteria.forEach {
			if (it.field == SearchableField.TITLE_NAME) {
				containTitleNameCriteria = true
			}
		}

		return searchManga(
			paginator = if (containTitleNameCriteria) {
				paginator
			} else {
				searchPaginator
			},
			searchQuery = searchQuery,
		)
	}

	public open suspend fun searchPageManga(searchQuery: MangaSearchQuery): List<Manga> {
		return getList(
			searchQuery.offset ?: 0,
			searchQuery.order ?: defaultSortOrder,
			convertToMangaListFilter(searchQuery),
		)
	}

	@Deprecated("New searchManga method should be preferred")
	final override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		return getList(
			paginator = if (filter.query.isNullOrEmpty()) {
				paginator
			} else {
				searchPaginator
			},
			offset = offset,
			order = order,
			filter = filter,
		)
	}

	@Deprecated("New searchManga method should be preferred")
	public abstract suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga>

	private suspend fun getList(
		paginator: Paginator,
		offset: Int,
		order: SortOrder,
		filter: MangaListFilter,
	): List<Manga> {
		val page = paginator.getPage(offset)
		val list = getListPage(page, order, filter)
		paginator.onListReceived(offset, page, list.size)
		return list
	}

	private suspend fun searchManga(
		paginator: Paginator,
		searchQuery: MangaSearchQuery,
	): List<Manga> {
		val offset: Int = searchQuery.offset ?: 0
		val page = paginator.getPage(offset)
		val list = searchPageManga(
			MangaSearchQuery.builder()
				.copy(searchQuery)
				.offset(page)
				.build(),
		)
		paginator.onListReceived(offset, page, list.size)
		return list
	}
}
