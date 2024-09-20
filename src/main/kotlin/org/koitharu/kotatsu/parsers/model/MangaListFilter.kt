package org.koitharu.kotatsu.parsers.model

import java.util.*

public data class MangaListFilter(
	@JvmField val query: String? = null,
	@JvmField val tags: Set<MangaTag> = emptySet(),
	@JvmField val tagsExclude: Set<MangaTag> = emptySet(),
	@JvmField val locale: Locale? = null,
	@JvmField val originalLocale: Locale? = null,
	@JvmField val states: Set<MangaState> = emptySet(),
	@JvmField val contentRating: Set<ContentRating> = emptySet(),
	@JvmField val types: Set<ContentType> = emptySet(),
	@JvmField val demographics: Set<Demographic> = emptySet(),
	@JvmField val year: Int = YEAR_UNKNOWN,
	@JvmField val yearFrom: Int = YEAR_UNKNOWN,
	@JvmField val yearTo: Int = YEAR_UNKNOWN,
) {

	public fun isEmpty(): Boolean = tags.isEmpty() &&
		tagsExclude.isEmpty() &&
		locale == null &&
		originalLocale == null &&
		states.isEmpty() &&
		contentRating.isEmpty() &&
		query == null &&
		year == YEAR_UNKNOWN &&
		yearFrom == YEAR_UNKNOWN &&
		yearTo == YEAR_UNKNOWN &&
		types.isEmpty() &&
		demographics.isEmpty()

	public companion object {

		@JvmStatic
		public val EMPTY: MangaListFilter = MangaListFilter()
	}
}
