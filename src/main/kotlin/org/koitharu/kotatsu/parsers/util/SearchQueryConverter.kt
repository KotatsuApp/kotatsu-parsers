package org.koitharu.kotatsu.parsers.util

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*

/**
 * Converts a [MangaListFilter] into a [MangaSearchQuery].
 *
 * This function iterates through the filter attributes in [MangaListFilter] and creates corresponding
 * search criteria in a [MangaSearchQuery.Builder].
 *
 * @param filter The [MangaListFilter] to convert.
 * @return A [MangaSearchQuery] constructed based on the given [filter].
 */
@InternalParsersApi
public fun convertToMangaSearchQuery(offset: Int, sortOrder: SortOrder, filter: MangaListFilter): MangaSearchQuery {
	return MangaSearchQuery.Builder().apply {
		offset(offset)
		order(sortOrder)
		if (filter.tags.isNotEmpty()) criterion(QueryCriteria.Include(TAG, filter.tags))
		if (filter.tagsExclude.isNotEmpty()) criterion(QueryCriteria.Exclude(TAG, filter.tagsExclude))
		if (filter.states.isNotEmpty()) criterion(QueryCriteria.Include(STATE, filter.states))
		if (filter.types.isNotEmpty()) criterion(QueryCriteria.Include(CONTENT_TYPE, filter.types))
		if (filter.contentRating.isNotEmpty()) criterion(QueryCriteria.Include(CONTENT_RATING, filter.contentRating))
		if (filter.demographics.isNotEmpty()) criterion(QueryCriteria.Include(DEMOGRAPHIC, filter.demographics))
		if (validateYear(filter.yearFrom) || validateYear(filter.yearTo)) {
			criterion(QueryCriteria.Range(PUBLICATION_YEAR, filter.yearFrom, filter.yearTo))
		}
		if (validateYear(filter.year)) {
			criterion(QueryCriteria.Match(PUBLICATION_YEAR, filter.year))
		}
		filter.locale?.takeIf { it != null }?.let {
			criterion(QueryCriteria.Include(LANGUAGE, setOf(it)))
		}
		filter.originalLocale?.takeIf { it != null }?.let {
			criterion(QueryCriteria.Include(ORIGINAL_LANGUAGE, setOf(it)))
		}
		filter.query?.takeIf { it.isNotBlank() }?.let {
			criterion(QueryCriteria.Match(TITLE_NAME, it))
		}
	}.build()
}

/**
 * Converts a {@link MangaSearchQuery} into a {@link MangaListFilter}.
 * <p>
 * This method iterates through the search criteria defined in the provided {@code searchQuery}
 * and applies them to a {@link MangaListFilter.Builder}. The criteria are processed based on
 * their types, such as inclusion, exclusion, equality checks, range filtering, and pattern matching.
 * </p>
 * <p>
 * Supported criteria:
 * <ul>
 *     <li>{@link QueryCriteria.Include} - Adds tags, states, content types, content ratings, demographics, and languages.</li>
 *     <li>{@link QueryCriteria.Exclude} - Excludes tags.</li>
 *     <li>{@link QueryCriteria.Equals} - Sets specific values like publication year.</li>
 *     <li>{@link QueryCriteria.Between} - Sets a range of values like publication year range.</li>
 *     <li>{@link QueryCriteria.Match} - Adds a search pattern for the title name.</li>
 * </ul>
 * </p>
 * <p>
 * If an unsupported field is encountered, an {@link UnsupportedOperationException} is thrown.
 * </p>
 *
 * @param searchQuery The {@link MangaSearchQuery} to convert.
 * @return A {@link MangaListFilter} constructed based on the given {@code searchQuery}.
 * @throws UnsupportedOperationException If the search criteria contain unsupported fields.
 */
@InternalParsersApi
public fun convertToMangaListFilter(searchQuery: MangaSearchQuery): MangaListFilter {
	return MangaListFilter.Builder().apply {
		for (criterion in searchQuery.criteria) {
			when (criterion) {
				is QueryCriteria.Include<*> -> handleInclude(this, criterion)
				is QueryCriteria.Exclude<*> -> handleExclude(this, criterion)
				is QueryCriteria.Range<*> -> handleBetween(this, criterion)
				is QueryCriteria.Match<*> -> handleMatch(this, criterion)
			}
		}
	}.build()
}

private fun handleInclude(builder: MangaListFilter.Builder, criterion: QueryCriteria.Include<*>) {
	val type = criterion.field.type

	when (criterion.field) {
		TAG -> builder.addTags(filterValues(criterion, type))
		STATE -> builder.addStates(filterValues(criterion, type))
		CONTENT_TYPE -> builder.addTypes(filterValues(criterion, type))
		CONTENT_RATING -> builder.addContentRatings(filterValues(criterion, type))
		DEMOGRAPHIC -> builder.addDemographics(filterValues(criterion, type))
		LANGUAGE -> builder.locale(getFirstValue(criterion, type))
		ORIGINAL_LANGUAGE -> builder.originalLocale(getFirstValue(criterion, type))
		else -> throw UnsupportedOperationException("Unsupported field for Include criterion: ${criterion.field}")
	}
}

private fun handleExclude(builder: MangaListFilter.Builder, criterion: QueryCriteria.Exclude<*>) {
	val type = criterion.field.type

	when (criterion.field) {
		TAG -> builder.excludeTags(filterValues(criterion, type))
		else -> throw UnsupportedOperationException("Unsupported field for Exclude criterion: ${criterion.field}")
	}
}

private fun handleBetween(builder: MangaListFilter.Builder, criterion: QueryCriteria.Range<*>) {
	val type = criterion.field.type

	when (criterion.field) {
		PUBLICATION_YEAR -> {
			builder.yearFrom(getValue(criterion.from, type, YEAR_UNKNOWN))
			builder.yearTo(getValue(criterion.to, type, YEAR_UNKNOWN))
		}
		else -> throw UnsupportedOperationException("Unsupported field for Between criterion: ${criterion.field}")
	}
}

private fun handleMatch(builder: MangaListFilter.Builder, criterion: QueryCriteria.Match<*>) {
	val type = criterion.field.type

	when (criterion.field) {
		TITLE_NAME -> builder.query(getValue(criterion.value, type, ""))
		PUBLICATION_YEAR -> builder.year(getValue(criterion.value, type, YEAR_UNKNOWN))
		else -> throw UnsupportedOperationException("Unsupported field for Match criterion: ${criterion.field}")
	}
}

@Suppress("UNCHECKED_CAST")
private fun <T> filterValues(criterion: QueryCriteria.Include<*>, type: Class<*>): List<T> {
	return criterion.values.filter { type.isInstance(it) } as List<T>
}

@Suppress("UNCHECKED_CAST")
private fun <T> filterValues(criterion: QueryCriteria.Exclude<*>, type: Class<*>): List<T> {
	return criterion.values.filter { type.isInstance(it) } as List<T>
}

@Suppress("UNCHECKED_CAST")
private fun <T> getFirstValue(criterion: QueryCriteria.Include<*>, type: Class<*>): T? {
	return criterion.values.firstOrNull { type.isInstance(it) } as? T
}

@Suppress("UNCHECKED_CAST")
private fun <T> getValue(value: Any?, type: Class<*>, default: T): T {
	val isCompatibleIntType = (type == Int::class.java && Integer::class.isInstance(value))

	return if (type.isInstance(value) || isCompatibleIntType) value as T else default
}

private fun validateYear(year: Int) = year != null && year != YEAR_UNKNOWN
