package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.model.MangaSource

class AuthRequiredException(
	val source: MangaSource,
) : RuntimeException("Authorization required")