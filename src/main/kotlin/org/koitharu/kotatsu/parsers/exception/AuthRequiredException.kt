package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * Authorization is required for access to the requested content
 */
class AuthRequiredException @InternalParsersApi @JvmOverloads constructor(
	val source: MangaSource,
	cause: Throwable? = null,
) : RuntimeException("Authorization required", cause)
