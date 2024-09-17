package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi
import java.util.*

data class MangaListFilterCapabilities @InternalParsersApi constructor(
	val availableSortOrders: Set<SortOrder>,
	val availableTags: Set<MangaTag>,
	val availableStates: Set<MangaState>,
	val availableContentRating: Set<ContentRating>,
	val availableContentTypes: Set<ContentType>,
	val availableDemographics: Set<Demographic>,
	val availableLocales: Set<Locale>,
	val isMultipleTagsSupported: Boolean,
	val isTagsExclusionSupported: Boolean,
	val isSearchSupported: Boolean,
	val searchSupportedWithMultipleFilters: Boolean,
	val isSearchYearSupported: Boolean,
	val isSearchYearRangeSupported: Boolean,
	val isSearchOriginalLanguages: Boolean,
)
