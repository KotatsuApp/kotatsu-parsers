package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi
import java.util.*

data class MangaListFilterOptions @InternalParsersApi constructor(
	val availableTags: Set<MangaTag>,
	val availableStates: Set<MangaState> = emptySet(),
	val availableContentRating: Set<ContentRating> = emptySet(),
	val availableContentTypes: Set<ContentType> = emptySet(),
	val availableDemographics: Set<Demographic> = emptySet(),
	val availableLocales: Set<Locale> = emptySet(),
)
