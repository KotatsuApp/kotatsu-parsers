package org.koitharu.kotatsu.parsers

import io.ktor.http.*
import kotlinx.io.IOException

class CloudFlareProtectedException(
	val url: String,
	val headers: Headers,
) : IOException("Protected by CloudFlare: $url")
