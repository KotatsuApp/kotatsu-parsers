package org.koitharu.kotatsu.parsers.model.search

import androidx.collection.ArraySet
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.util.mapToSet

@ExposedCopyVisibility
public data class MangaSearchQueryCapabilities internal constructor(
	public val capabilities: Set<SearchCapability>,
) {

	public constructor(vararg capabilities: SearchCapability) : this(ArraySet(capabilities))

	@InternalParsersApi
	public fun validate(query: MangaSearchQuery) {
		val strictFields = capabilities.filter { !it.otherCriteria }.mapToSet { it.field }
		val usedStrictFields = query.criteria.mapToSet { it.field }.intersect(strictFields)

		if (usedStrictFields.isNotEmpty() && query.criteria.size > 1) {
			throw IllegalArgumentException(
				"Query contains multiple criteria, but at least one field (${usedStrictFields.joinToString()}) does not support multiple criteria.",
			)
		}

		for (criterion in query.criteria) {
			val capability = capabilities.find { it.field == criterion.field }
				?: throw IllegalArgumentException("Unsupported search field: ${criterion.field}")

			if (criterion::class !in capability.criteriaTypes) {
				throw IllegalArgumentException(
					"Unsupported search criterion: ${criterion::class.simpleName} for field ${criterion.field}",
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
