package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi
import java.util.*

data class MangaListFilterOptions @InternalParsersApi constructor(

	/**
	 * Available tags (genres)
	 */
	val availableTags: Set<MangaTag>,

	/**
	 * Supported [MangaState] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	val availableStates: Set<MangaState> = emptySet(),

	/**
	 * Supported [ContentRating] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	val availableContentRating: Set<ContentRating> = emptySet(),

	/**
	 * Supported [ContentType] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	val availableContentTypes: Set<ContentType> = emptySet(),

	/**
	 * Supported [Demographic] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	val availableDemographics: Set<Demographic> = emptySet(),

	/**
	 * Supported content locales for multilingual sources
	 */
	val availableLocales: Set<Locale> = emptySet(),
)
