package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilterV2
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder

@InternalParsersApi
abstract class SinglePageMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : MangaParser(context, source) {

	final override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilterV2): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	abstract suspend fun getList(order: SortOrder, filter: MangaListFilterV2): List<Manga>
}
