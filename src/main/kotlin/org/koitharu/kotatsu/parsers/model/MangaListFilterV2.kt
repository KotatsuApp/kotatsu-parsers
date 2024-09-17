package org.koitharu.kotatsu.parsers.model

import java.util.*

data class MangaListFilterV2(
	@JvmField val query: String? = null,
	@JvmField val tags: Set<MangaTag> = emptySet(),
	@JvmField val tagsExclude: Set<MangaTag> = emptySet(),
	@JvmField val locale: Locale? = null,
	@JvmField val sourceLocale: Locale? = null,
	@JvmField val states: Set<MangaState> = emptySet(),
	@JvmField val contentRating: Set<ContentRating> = emptySet(),
	@JvmField val types: Set<ContentType> = emptySet(),
	@JvmField val demographics: Set<Demographic> = emptySet(),
	@JvmField val year: Int = 0,
	@JvmField val yearFrom: Int = 0,
	@JvmField val yearTo: Int = 0,
) {

	fun isEmpty(): Boolean = tags.isEmpty() &&
		tagsExclude.isEmpty() &&
		locale == null &&
		sourceLocale == null &&
		states.isEmpty() &&
		contentRating.isEmpty() &&
		query == null &&
		year == 0 &&
		yearFrom == 0 &&
		yearTo == 0 &&
		types.isEmpty() &&
		demographics.isEmpty()

	companion object {

		@JvmStatic
		val EMPTY = MangaListFilterV2()
	}
}
