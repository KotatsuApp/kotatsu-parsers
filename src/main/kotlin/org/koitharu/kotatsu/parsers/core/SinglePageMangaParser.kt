package org.koitharu.kotatsu.parsers.core

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery

@InternalParsersApi
public abstract class SinglePageMangaParser(
	context: MangaLoaderContext,
	source: MangaParserSource,
) : AbstractMangaParser(context, source) {

	final override suspend fun getList(query: MangaSearchQuery): List<Manga> {
		if (query.offset > 0) {
			return emptyList()
		}
		return getSinglePageList(query)
	}

	public abstract suspend fun getSinglePageList(searchQuery: MangaSearchQuery): List<Manga>
}
