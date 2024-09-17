package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.MangaParser
import java.util.*

@Deprecated("Use MangaListFilterV2 instead")
sealed interface MangaListFilter {

	fun isEmpty(): Boolean

	val sortOrder: SortOrder?

	fun isValid(parser: MangaParser): Boolean = when (this) {
		is Advanced -> (sortOrder in parser.availableSortOrders) &&
			(tags.size <= 1 || parser.isMultipleTagsSupported) &&
			(tagsExclude.isEmpty() || parser.isTagsExclusionSupported) &&
			(contentRating.isEmpty() || parser.availableContentRating.containsAll(contentRating)) &&
			(states.isEmpty() || parser.availableStates.containsAll(states) &&
				(parser.searchSupportedWithMultipleFilters) && (parser.isSearchOriginalLanguages) &&
				(parser.isSearchYearSupported) && (parser.isSearchYearRangeSupported)) &&
			(types.isEmpty() || parser.availableContentTypes.containsAll(types)) &&
			(demographics.isEmpty() || parser.availableDemographics.containsAll(demographics))

		is Search -> parser.isSearchSupported
	}

	data class Search(
		@JvmField val query: String,
	) : MangaListFilter {

		override val sortOrder: SortOrder? = null

		override fun isEmpty() = query.isBlank()
	}

	data class Advanced(
		override val sortOrder: SortOrder,
		@JvmField val tags: Set<MangaTag>,
		@JvmField val tagsExclude: Set<MangaTag>,
		@JvmField val locale: Locale?,
		@JvmField val localeMangas: Locale?,
		@JvmField val states: Set<MangaState>,
		@JvmField val contentRating: Set<ContentRating>,
		@JvmField val query: String?,
		@JvmField val year: Int?,
		@JvmField val yearFrom: Int?,
		@JvmField val yearTo: Int?,
		@JvmField val types: Set<ContentType>,
		@JvmField val demographics: Set<Demographic>,
	) : MangaListFilter {

		override fun isEmpty(): Boolean =
			tags.isEmpty() && tagsExclude.isEmpty() && locale == null && localeMangas == null && states.isEmpty() && contentRating.isEmpty() && query == null && year == null && yearFrom == null && yearTo == null && types.isEmpty() && demographics.isEmpty()

		fun newBuilder() = Builder(sortOrder)
			.tags(tags)
			.tagsExclude(tagsExclude)
			.locale(locale)
			.localeMangas(localeMangas)
			.states(states)
			.contentRatings(contentRating)
			.query(query)
			.year(year)
			.yearFrom(yearFrom)
			.yearTo(yearTo)
			.type(types)
			.demographic(demographics)

		class Builder(sortOrder: SortOrder) {

			private var _sortOrder: SortOrder = sortOrder
			private var _tags: Set<MangaTag>? = null
			private var _tagsExclude: Set<MangaTag>? = null
			private var _locale: Locale? = null
			private var _localeMangas: Locale? = null
			private var _states: Set<MangaState>? = null
			private var _contentRating: Set<ContentRating>? = null
			private var _query: String? = null
			private var _year: Int? = null
			private var _yearFrom: Int? = null
			private var _yearTo: Int? = null
			private var _types: Set<ContentType>? = null
			private var _demographic: Set<Demographic>? = null

			fun sortOrder(order: SortOrder) = apply {
				_sortOrder = order
			}

			fun tags(tags: Set<MangaTag>?) = apply {
				_tags = tags
			}

			fun tagsExclude(tags: Set<MangaTag>?) = apply {
				_tagsExclude = tags
			}

			fun locale(locale: Locale?) = apply {
				_locale = locale
			}

			fun localeMangas(localeMangas: Locale?) = apply {
				_localeMangas = localeMangas
			}

			fun states(states: Set<MangaState>?) = apply {
				_states = states
			}

			fun contentRatings(rating: Set<ContentRating>?) = apply {
				_contentRating = rating
			}

			fun query(query: String?) = apply {
				_query = query
			}

			fun year(year: Int?) = apply {
				_year = year
			}

			fun yearFrom(yearFrom: Int?) = apply {
				_yearFrom = yearFrom
			}

			fun yearTo(yearTo: Int?) = apply {
				_yearTo = yearTo
			}

			fun type(type: Set<ContentType>?) = apply {
				_types = type
			}

			fun demographic(demographic: Set<Demographic>?) = apply {
				_demographic = demographic
			}

			fun build() = Advanced(
				sortOrder = _sortOrder,
				tags = _tags.orEmpty(),
				tagsExclude = _tagsExclude.orEmpty(),
				locale = _locale,
				localeMangas = _localeMangas,
				states = _states.orEmpty(),
				contentRating = _contentRating.orEmpty(),
				query = _query,
				year = _year,
				yearFrom = _yearFrom,
				yearTo = _yearTo,
				types = _types.orEmpty(),
				demographics = _demographic.orEmpty(),
			)
		}
	}
}
