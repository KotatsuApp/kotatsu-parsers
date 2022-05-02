package org.koitharu.kotatsu.parsers.exception

import okio.IOException

class CloudFlareProtectedException(
	val url: String,
) : IOException("Protected by CloudFlare: $url")