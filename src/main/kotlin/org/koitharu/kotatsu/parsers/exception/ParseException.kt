package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.InternalParsersApi

class ParseException @InternalParsersApi @JvmOverloads constructor(
	message: String?,
	cause: Throwable? = null,
) : RuntimeException(message, cause)