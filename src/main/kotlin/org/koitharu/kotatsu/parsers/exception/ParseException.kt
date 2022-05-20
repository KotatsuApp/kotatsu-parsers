package org.koitharu.kotatsu.parsers.exception

class ParseException @JvmOverloads constructor(
	message: String?,
	cause: Throwable? = null,
) : RuntimeException(message, cause)