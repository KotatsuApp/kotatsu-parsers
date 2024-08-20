package org.koitharu.kotatsu.parsers.exception

import okio.IOException
import java.time.Instant

class TooManyRequestExceptions(
	val url: String,
	val retryAfter: Long,
) : IOException() {

	val retryAt: Instant?
		get() = if (retryAfter > 0 && retryAfter < Long.MAX_VALUE) {
			Instant.now().plusMillis(retryAfter)
		} else {
			null
		}
}
