package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi

data class MangaListFilterCapabilities @InternalParsersApi constructor(

	/**
	 * Whether parser supports filtering by more than one tag
	 * @see [MangaListFilterV2.tags]
	 * @see [MangaListFilterOptions.availableTags]
	 */
	val isMultipleTagsSupported: Boolean,

	/**
	 * Whether parser supports tagsExclude field in filter
	 * @see [MangaListFilterV2.tagsExclude]
	 * @see [MangaListFilterOptions.availableTags]
	 */
	val isTagsExclusionSupported: Boolean,

	/**
	 * Whether parser supports searching by string query
	 * @see [MangaListFilterV2.query]
	 */
	val isSearchSupported: Boolean,

	/**
	 * Whether parser supports searching by string query combined within other filters
	 */
	val isSearchWithFiltersSupported: Boolean,

	/**
	 * Whether parser supports searching/filtering by year
	 * @see [MangaListFilterV2.year]
	 */
	val isYearSupported: Boolean = false,

	/**
	 * Whether parser supports searching by year range
	 * @see [MangaListFilterV2.yearFrom] and [MangaListFilterV2.yearTo]
	 */
	val isYearRangeSupported: Boolean = false,

	/**
	 * Whether parser supports searching Original Languages
	 * @see [MangaListFilterV2.originalLocale]
	 * @see [MangaListFilterOptions.availableLocales]
	 */
	val isOriginalLocaleSupported: Boolean = false,
)
