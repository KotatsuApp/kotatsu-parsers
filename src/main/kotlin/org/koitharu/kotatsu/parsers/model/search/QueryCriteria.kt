package org.koitharu.kotatsu.parsers.model.search

/**
 * Represents a generic search criterion used for filtering manga search results.
 * Each criterion applies a specific condition to a [SearchableField] and operates on values of type [T].
 *
 * @param T The type of value associated with the search criterion.
 * @property field The field to which this search criterion applies.
 */
public sealed class QueryCriteria<T>(
	@JvmField public val field: SearchableField
) {
	public class Include<T : Any>(field: SearchableField, @JvmField public val values: Set<T>) : QueryCriteria<T>(field) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Include<*>) return false
			return field == other.field && values == other.values
		}

		override fun hashCode(): Int {
			return 31 * field.hashCode() + values.hashCode()
		}
	}

	public class Exclude<T : Any>(field: SearchableField, @JvmField public val values: Set<T>) : QueryCriteria<T>(field) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Exclude<*>) return false
			return field == other.field && values == other.values
		}

		override fun hashCode(): Int {
			return 31 * field.hashCode() + values.hashCode()
		}
	}

	public class Range<T : Comparable<T>>(field: SearchableField, @JvmField public val from: T, @JvmField public val to: T) : QueryCriteria<T>(field) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Range<*>) return false
			return field == other.field && from == other.from && to == other.to
		}

		override fun hashCode(): Int {
			var result = field.hashCode()
			result = 31 * result + from.hashCode()
			result = 31 * result + to.hashCode()
			return result
		}
	}

	public class Match<T : Any>(field: SearchableField, @JvmField public val value: T) : QueryCriteria<T>(field) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is Match<*>) return false
			return field == other.field && value == other.value
		}

		override fun hashCode(): Int {
			return 31 * field.hashCode() + value.hashCode()
		}
	}
}
