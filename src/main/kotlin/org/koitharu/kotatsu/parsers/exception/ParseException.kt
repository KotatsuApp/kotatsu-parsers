package org.koitharu.kotatsu.parsers.exception

import org.koitharu.kotatsu.parsers.InternalParsersApi

class ParseException @InternalParsersApi @JvmOverloads constructor(
	val shortMessage: String?,
	val url: String,
	cause: Throwable? = null,
) : RuntimeException("$shortMessage at $url", cause)