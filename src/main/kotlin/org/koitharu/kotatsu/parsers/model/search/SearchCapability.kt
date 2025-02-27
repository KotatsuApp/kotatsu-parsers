package org.koitharu.kotatsu.parsers.model.search

import kotlin.reflect.KClass

public data class SearchCapability (
	@JvmField public val field: SearchableField,
	@JvmField public val criteriaTypes: Set<KClass<out QueryCriteria<*>>>,
	@JvmField public val multiValue: Boolean,
	@JvmField public val otherCriteria: Boolean,
)
