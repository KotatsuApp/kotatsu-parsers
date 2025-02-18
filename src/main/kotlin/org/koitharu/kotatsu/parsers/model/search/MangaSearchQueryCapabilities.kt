package org.koitharu.kotatsu.parsers.model.search

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*

public data class MangaSearchQueryCapabilities(
	val capabilities: Set<SearchCapability> = emptySet(),
) {
	public companion object {
		@InternalParsersApi
		public fun from(filterCapabilities: MangaListFilterCapabilities): MangaSearchQueryCapabilities {
			return MangaSearchQueryCapabilities(
				capabilities = setOfNotNull(
					filterCapabilities.isMultipleTagsSupported.takeIf { it }?.let {
						SearchCapability(
							field = TAG, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
						)
					},
					filterCapabilities.isTagsExclusionSupported.takeIf { it }?.let {
						SearchCapability(
							field = TAG, criteriaTypes = setOf(Exclude::class), multiValue = true, otherCriteria = true
						)
					},
					filterCapabilities.isSearchSupported.takeIf { it }?.let {
						SearchCapability(
							field = TITLE_NAME, criteriaTypes = setOf(Match::class), multiValue = false, otherCriteria = false
						)
					},
					filterCapabilities.isSearchWithFiltersSupported.takeIf { it }?.let {
						SearchCapability(
							field = TITLE_NAME, criteriaTypes = setOf(Match::class), multiValue = false, otherCriteria = true
						)
					},
					filterCapabilities.isYearSupported.takeIf { it }?.let {
						SearchCapability(
							field = PUBLICATION_YEAR, criteriaTypes = setOf(Match::class), multiValue = false, otherCriteria = true
						)
					},
					filterCapabilities.isYearRangeSupported.takeIf { it }?.let {
						SearchCapability(
							field = PUBLICATION_YEAR, criteriaTypes = setOf(Range::class), multiValue = false, otherCriteria = true
						)
					},
					filterCapabilities.isOriginalLocaleSupported.takeIf { it }?.let {
						SearchCapability(
							field = ORIGINAL_LANGUAGE, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
						)
					},
					SearchCapability(
						field = LANGUAGE, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
					),
					SearchCapability(
						field = STATE, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
					),
					SearchCapability(
						field = CONTENT_TYPE, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
					),
					SearchCapability(
						field = CONTENT_RATING, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
					),
					SearchCapability(
						field = DEMOGRAPHIC, criteriaTypes = setOf(Include::class), multiValue = true, otherCriteria = true
					),
				),
			)
		}
	}

	@InternalParsersApi
	public fun validate(query: MangaSearchQuery) {
		val strictFields = capabilities.filter { !it.otherCriteria }.map { it.field }.toSet()
		val usedStrictFields = query.criteria.map { it.field }.toSet().intersect(strictFields)

		if (usedStrictFields.isNotEmpty() && query.criteria.size > 1) {
			throw IllegalArgumentException(
				"Query contains multiple criteria, but at least one field (${usedStrictFields.joinToString()}) does not support multiple criteria."
			)
		}

		for (criterion in query.criteria) {
			val capability = capabilities.find { it.field == criterion.field }
				?: throw IllegalArgumentException("Unsupported search field: ${criterion.field}")

			if (criterion::class !in capability.criteriaTypes) {
				throw IllegalArgumentException(
					"Unsupported search criterion: ${criterion::class.simpleName} for field ${criterion.field}"
				)
			}

			// Ensure single value per criterion if supportMultiValue is false
			if (!capability.multiValue) {
				when (criterion) {
					is Include<*> -> if (criterion.values.size > 1)
						throw IllegalArgumentException("Multiple values are not allowed for field ${criterion.field}")
					is Exclude<*> -> if (criterion.values.size > 1)
						throw IllegalArgumentException("Multiple values are not allowed for field ${criterion.field}")
					is Range<*> -> {} // Range is always valid (from, to)
					is Match<*> -> {} // Match always has a single value
				}
			}
		}
	}
}
