package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi

data class MangaListFilterCapabilities @InternalParsersApi constructor(
	val isMultipleTagsSupported: Boolean,
	val isTagsExclusionSupported: Boolean,
	val isSearchSupported: Boolean,
	val isSearchWithFiltersSupported: Boolean,
	val isYearSupported: Boolean = false,
	val isYearRangeSupported: Boolean = false,
	val isSourceLocaleSupported: Boolean = false,
)
