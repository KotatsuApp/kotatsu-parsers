package org.koitharu.kotatsu.parsers.model

import java.util.*

@Suppress("DataClassPrivateConstructor")
data class MangaListFilterV2 private constructor(
	@JvmField val sortOrder: SortOrder?,
	@JvmField val tags: Set<MangaTag>,
	@JvmField val tagsExclude: Set<MangaTag>,
	@JvmField val locale: Locale?,
	@JvmField val localeMangas: Locale?,
	@JvmField val states: Set<MangaState>,
	@JvmField val contentRating: Set<ContentRating>,
	@JvmField val query: String?,
	@JvmField val year: Int,
	@JvmField val yearFrom: Int,
	@JvmField val yearTo: Int,
) {

	fun isEmpty(): Boolean = tags.isEmpty() &&
		tagsExclude.isEmpty() &&
		locale == null &&
		localeMangas == null &&
		states.isEmpty() &&
		contentRating.isEmpty() &&
		query == null &&
		year == 0 &&
		yearFrom == 0 &&
		yearTo == 0

	fun newBuilder() = Builder()
		.sortOrder(sortOrder)
		.tags(tags)
		.tagsExclude(tagsExclude)
		.locale(locale)
		.localeMangas(localeMangas)
		.states(states)
		.contentRatings(contentRating)
		.searchQuery(query)
		.year(year)
		.yearFrom(yearFrom)
		.yearTo(yearTo)

	class Builder {

		private var _sortOrder: SortOrder? = null
		private var _tags: Set<MangaTag>? = null
		private var _tagsExclude: Set<MangaTag>? = null
		private var _locale: Locale? = null
		private var _localeMangas: Locale? = null
		private var _states: Set<MangaState>? = null
		private var _contentRating: Set<ContentRating>? = null
		private var _query: String? = null
		private var _year: Int = 0
		private var _yearFrom: Int = 0
		private var _yearTo: Int = 0

		fun sortOrder(order: SortOrder?) = apply {
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

		fun searchQuery(query: String?) = apply {
			_query = query
		}

		fun year(year: Int) = apply {
			_year = year
		}

		fun yearFrom(yearFrom: Int) = apply {
			_yearFrom = yearFrom
		}

		fun yearTo(yearTo: Int) = apply {
			_yearTo = yearTo
		}

		fun build() = MangaListFilterV2(
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
		)
	}

	companion object {

		@JvmStatic
		val EMPTY = Builder().build()
	}
}
