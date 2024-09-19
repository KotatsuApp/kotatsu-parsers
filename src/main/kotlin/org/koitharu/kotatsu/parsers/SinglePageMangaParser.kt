package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder

@InternalParsersApi
public abstract class SinglePageMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : MangaParser(context, source) {

	final override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	public abstract suspend fun getList(order: SortOrder, filter: MangaListFilter): List<Manga>
}
