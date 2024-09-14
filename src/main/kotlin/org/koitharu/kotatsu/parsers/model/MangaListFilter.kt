package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.MangaParser
import java.util.*

sealed interface MangaListFilter {

	fun isEmpty(): Boolean

	val sortOrder: SortOrder?

	fun isValid(parser: MangaParser): Boolean = when (this) {
		is Advanced -> (sortOrder in parser.availableSortOrders) &&
			(tags.size <= 1 || parser.isMultipleTagsSupported) &&
			(tagsExclude.isEmpty() || parser.isTagsExclusionSupported) &&
			(contentRating.isEmpty() || parser.availableContentRating.containsAll(contentRating)) &&
			(states.isEmpty() || parser.availableStates.containsAll(states) &&
			(type.isEmpty() || parser.availableType.containsAll(type)) &&
			(demographic.isEmpty() || parser.availableDemographic.containsAll(demographic)))

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
		@JvmField val states: Set<MangaState>,
		@JvmField val contentRating: Set<ContentRating>,
		@JvmField val type: Set<Type>,
		@JvmField val demographic: Set<Demographic>,
	) : MangaListFilter {

		override fun isEmpty(): Boolean =
			tags.isEmpty() && tagsExclude.isEmpty() && locale == null && states.isEmpty() && contentRating.isEmpty() && type.isEmpty() && demographic.isEmpty()

		fun newBuilder() = Builder(sortOrder)
			.tags(tags)
			.tagsExclude(tagsExclude)
			.locale(locale)
			.states(states)
			.contentRatings(contentRating)
			.type(type)
			.demographic(demographic)

		class Builder(sortOrder: SortOrder) {

			private var _sortOrder: SortOrder = sortOrder
			private var _tags: Set<MangaTag>? = null
			private var _tagsExclude: Set<MangaTag>? = null
			private var _locale: Locale? = null
			private var _states: Set<MangaState>? = null
			private var _contentRating: Set<ContentRating>? = null
			private var _type: Set<Type>? = null
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

			fun states(states: Set<MangaState>?) = apply {
				_states = states
			}

			fun contentRatings(rating: Set<ContentRating>?) = apply {
				_contentRating = rating
			}

			fun type(type: Set<Type>?) = apply {
				_type = type
			}

			fun demographic(demographic: Set<Demographic>?) = apply {
				_demographic = demographic
			}

			fun build() = Advanced(
				sortOrder = _sortOrder,
				tags = _tags.orEmpty(),
				tagsExclude = _tagsExclude.orEmpty(),
				locale = _locale,
				states = _states.orEmpty(),
				contentRating = _contentRating.orEmpty(),
				type = _type.orEmpty(),
				demographic = _demographic.orEmpty()
			)
		}
	}
}
