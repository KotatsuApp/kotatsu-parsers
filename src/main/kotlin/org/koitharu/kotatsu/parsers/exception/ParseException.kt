package org.koitharu.kotatsu.parsers.exception

class ParseException(
	message: String? = null,
	cause: Throwable? = null,
) : RuntimeException(message, cause)