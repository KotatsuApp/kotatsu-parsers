package org.koitharu.kotatsu.parsers

object ErrorMessages {

	const val FILTER_MULTIPLE_STATES_NOT_SUPPORTED = "Multiple states are not supported by this source"
	const val FILTER_MULTIPLE_GENRES_NOT_SUPPORTED = "Multiple genres are not supported by this source"
	const val FILTER_MULTIPLE_CONTENT_RATING_NOT_SUPPORTED =
		"Multiple Content Rating are not supported by this source"
	const val FILTER_BOTH_LOCALE_GENRES_NOT_SUPPORTED =
		"Filtering by both genres and locale is not supported by this source"
	const val FILTER_BOTH_STATES_GENRES_NOT_SUPPORTED =
		"Filtering by both genres and states is not supported by this source"
	const val SEARCH_NOT_SUPPORTED = "Search is not supported by this source"
}
