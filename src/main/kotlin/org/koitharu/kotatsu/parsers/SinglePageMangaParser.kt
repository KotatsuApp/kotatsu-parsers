package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.util.convertToMangaListFilter

@InternalParsersApi
public abstract class SinglePageMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : MangaParser(context, source) {


	final override suspend fun validatedMangaSearch(searchQuery: MangaSearchQuery): List<Manga> {
		if (searchQuery.offset != null && searchQuery.offset > 0) {
			return emptyList()
		}
		return searchSinglePageManga(searchQuery)
	}

	public open suspend fun searchSinglePageManga(searchQuery: MangaSearchQuery): List<Manga> {
		return getList(
			searchQuery.offset ?: 0,
			searchQuery.order ?: defaultSortOrder,
			convertToMangaListFilter(searchQuery),
		)
	}

	@Deprecated("New searchManga method should be preferred")
	final override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	@Deprecated("New searchManga method should be preferred")
	public abstract suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga>
}
