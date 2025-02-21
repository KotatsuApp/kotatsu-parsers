package org.koitharu.kotatsu.parsers.model

import java.util.*

@Deprecated("Please check new searchManga method and MangaSearchQuery class")
public data class MangaListFilter(
	@JvmField val query: String? = null,
	@JvmField val tags: Set<MangaTag> = emptySet(),
	@JvmField val tagsExclude: Set<MangaTag> = emptySet(),
	@JvmField val locale: Locale? = null,
	@JvmField val originalLocale: Locale? = null,
	@JvmField val states: Set<MangaState> = emptySet(),
	@JvmField val contentRating: Set<ContentRating> = emptySet(),
	@JvmField val types: Set<ContentType> = emptySet(),
	@JvmField val demographics: Set<Demographic> = emptySet(),
	@JvmField val year: Int = YEAR_UNKNOWN,
	@JvmField val yearFrom: Int = YEAR_UNKNOWN,
	@JvmField val yearTo: Int = YEAR_UNKNOWN,
	@JvmField val author: String? = null,
) {

	private fun isNonSearchOptionsEmpty(): Boolean = tags.isEmpty() &&
		tagsExclude.isEmpty() &&
		locale == null &&
		originalLocale == null &&
		states.isEmpty() &&
		contentRating.isEmpty() &&
		year == YEAR_UNKNOWN &&
		yearFrom == YEAR_UNKNOWN &&
		yearTo == YEAR_UNKNOWN &&
		types.isEmpty() &&
		demographics.isEmpty() &&
		author.isNullOrEmpty()

	public fun isEmpty(): Boolean = isNonSearchOptionsEmpty() && query.isNullOrEmpty()

	public fun isNotEmpty(): Boolean = !isEmpty()

	public fun hasNonSearchOptions(): Boolean = !isNonSearchOptionsEmpty()

	public companion object {

		@JvmStatic
		public val EMPTY: MangaListFilter = MangaListFilter()
	}

	public class Builder {
		private var query: String? = null
		private val tags: MutableSet<MangaTag> = mutableSetOf()
		private val tagsExclude: MutableSet<MangaTag> = mutableSetOf()
		private var locale: Locale? = null
		private var originalLocale: Locale? = null
		private val states: MutableSet<MangaState> = mutableSetOf()
		private val contentRating: MutableSet<ContentRating> = mutableSetOf()
		private val types: MutableSet<ContentType> = mutableSetOf()
		private val demographics: MutableSet<Demographic> = mutableSetOf()
		private var year: Int = YEAR_UNKNOWN
		private var yearFrom: Int = YEAR_UNKNOWN
		private var yearTo: Int = YEAR_UNKNOWN

		public fun query(query: String?): Builder = apply { this.query = query }
		public fun addTag(tag: MangaTag): Builder = apply { tags.add(tag) }
		public fun addTags(tags: Collection<MangaTag>): Builder = apply { this.tags.addAll(tags) }
		public fun excludeTag(tag: MangaTag): Builder = apply { tagsExclude.add(tag) }
		public fun excludeTags(tags: Collection<MangaTag>): Builder = apply { this.tagsExclude.addAll(tags) }
		public fun locale(locale: Locale?): Builder = apply { this.locale = locale }
		public fun originalLocale(locale: Locale?): Builder = apply { this.originalLocale = locale }
		public fun addState(state: MangaState): Builder = apply { states.add(state) }
		public fun addStates(states: Collection<MangaState>): Builder = apply { this.states.addAll(states) }
		public fun addContentRating(rating: ContentRating): Builder = apply { contentRating.add(rating) }
		public fun addContentRatings(ratings: Collection<ContentRating>): Builder = apply { this.contentRating.addAll(ratings) }
		public fun addType(type: ContentType): Builder = apply { types.add(type) }
		public fun addTypes(types: Collection<ContentType>): Builder = apply { this.types.addAll(types) }
		public fun addDemographic(demographic: Demographic): Builder = apply { demographics.add(demographic) }
		public fun addDemographics(demographics: Collection<Demographic>): Builder = apply { this.demographics.addAll(demographics) }
		public fun year(year: Int): Builder = apply { this.year = year }
		public fun yearFrom(year: Int): Builder = apply { this.yearFrom = year }
		public fun yearTo(year: Int): Builder = apply { this.yearTo = year }

		public fun build(): MangaListFilter = MangaListFilter(
			query, tags, tagsExclude, locale, originalLocale, states,
			contentRating, types, demographics, year, yearFrom, yearTo
		)
	}
}
