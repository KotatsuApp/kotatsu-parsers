package org.koitharu.kotatsu.parsers.model.search

/**
 * Represents a generic search criterion used for filtering manga search results.
 * Each criterion applies a specific condition to a [SearchableField] and operates on values of type [T].
 *
 * @param T The type of value associated with the search criterion.
 * @property field The field to which this search criterion applies.
 */
public sealed interface QueryCriteria<T> {

	public val field: SearchableField

	override fun equals(other: Any?): Boolean

	override fun hashCode(): Int

	public data class Include<T : Any>(
		public override val field: SearchableField,
		@JvmField public val values: Set<T>,
	) : QueryCriteria<T> {

		init {
			check(values.all { x -> field.type.isInstance(x) })
		}
	}

	public data class Exclude<T : Any>(
		public override val field: SearchableField,
		@JvmField public val values: Set<T>,
	) : QueryCriteria<T> {

		init {
			check(values.all { x -> field.type.isInstance(x) })
		}
	}

	public data class Range<T : Comparable<T>>(
		public override val field: SearchableField,
		@JvmField public val from: T,
		@JvmField public val to: T,
	) : QueryCriteria<T> {

		init {
			check(field.type.isInstance(from))
			check(field.type.isInstance(to))
		}
	}

	public data class Match<T : Any>(
		public override val field: SearchableField,
		@JvmField public val value: T,
	) : QueryCriteria<T> {

		init {
			check(field.type.isInstance(value))
		}
	}
}
