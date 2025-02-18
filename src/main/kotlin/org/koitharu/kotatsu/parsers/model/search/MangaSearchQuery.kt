package org.koitharu.kotatsu.parsers.model.search

import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Represents a search query for filtering and sorting manga search results.
 * This class is immutable and must be constructed using the [Builder].
 *
 * @property criteria The set of search criteria applied to the query.
 * @property order The sorting order for the results (optional).
 * @property offset The offset number for paginated search results (optional).
 */
public class MangaSearchQuery private constructor(
	@JvmField public val criteria: Set<QueryCriteria<*>> = emptySet(),
	@JvmField public val order: SortOrder? = null,
	@JvmField public val offset: Int? = null,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is MangaSearchQuery) return false

		return criteria == other.criteria &&
			order == other.order &&
			offset == other.offset
	}

	override fun hashCode(): Int {
		var result = criteria.hashCode()
		result = 31 * result + (order?.hashCode() ?: 0)
		result = 31 * result + (offset ?: 0)
		return result
	}

	public companion object {
		public fun builder(): Builder = Builder()
	}

	public class Builder {
		private var criteria: MutableSet<QueryCriteria<*>> = mutableSetOf()
		private var order: SortOrder? = null
		private var offset: Int? = null

		public fun copy(searchQuery: MangaSearchQuery): Builder = apply {
			this.criteria = searchQuery.criteria as MutableSet<QueryCriteria<*>>
			this.order = searchQuery.order
			this.offset = searchQuery.offset
		}

		@Throws(IllegalArgumentException::class)
		public fun criterion(criterion: QueryCriteria<*>): Builder = apply {
			validateCriterion(criterion)
			this.criteria.add(criterion)
		}

		public fun order(order: SortOrder?): Builder = apply { this.order = order }

		public fun offset(offset: Int?): Builder = apply { this.offset = offset }

		@Throws(IllegalArgumentException::class)
		public fun build(): MangaSearchQuery {
			return MangaSearchQuery(deduplicateCriteria(criteria), order, offset)
		}

		/**
		 * Validates the provided [QueryCriteria] to ensure type correctness.
		 *
		 * @param criterion The search criterion to validate.
		 * @throws IllegalArgumentException If the criterion type does not match the expected type.
		 */
		private fun validateCriterion(criterion: QueryCriteria<*>) {
			try {
				val expectedType = criterion.field.type
				val actualType: Class<*>? = when (criterion) {
					is QueryCriteria.Include<*> -> criterion.values.first().javaClass
					is QueryCriteria.Exclude<*> -> criterion.values.first().javaClass
					is QueryCriteria.Match<*> -> criterion.value.javaClass
					is QueryCriteria.Range<*> -> {
						if (criterion.from.javaClass != criterion.to.javaClass) {
							throw IllegalArgumentException(
								"Mismatched types for field '${criterion.field}'. 'from' and 'to' should have same types"
							)
						}
						criterion.from.javaClass
					}
				}

				val isCompatibleIntType = (expectedType == Int::class.java && actualType == Integer::class.java) ||
					(expectedType == Integer::class.java && actualType == Int::class.java)

				if (actualType != null && !expectedType.isAssignableFrom(actualType) && !isCompatibleIntType) {
					throw IllegalArgumentException(
						"Invalid type for ${criterion.field}. Expected: ${expectedType.simpleName}, but got: ${actualType.simpleName}"
					)
				}
			} catch (e: NoSuchElementException) {
				throw IllegalArgumentException(
					"QueryCriteria values should not be empty"
				)
			}
		}

		private fun deduplicateCriteria(criteria: Set<QueryCriteria<*>>): Set<QueryCriteria<*>> {
			val uniqueCriteria = mutableMapOf<Pair<SearchableField, Class<out QueryCriteria<*>>>, QueryCriteria<*>>()

			for (criterion in criteria) {
				val key = criterion.field to criterion::class.java
				val existing = uniqueCriteria[key]

				when {
					existing == null -> uniqueCriteria[key] = criterion

					existing is QueryCriteria.Include<*> && criterion is QueryCriteria.Include<*> -> {
						uniqueCriteria[key] = QueryCriteria.Include(criterion.field,existing.values union criterion.values)
					}

					existing is QueryCriteria.Exclude<*> && criterion is QueryCriteria.Exclude<*> -> {
						uniqueCriteria[key] = QueryCriteria.Exclude(criterion.field,existing.values union criterion.values)
					}

					else -> throw IllegalArgumentException(
						"Match and Range have only one criterion per type, but found duplicates for: ${criterion.field} in ${criterion::class.simpleName}"
					)
				}
			}

			return uniqueCriteria.values.toSet()
		}
	}
}
