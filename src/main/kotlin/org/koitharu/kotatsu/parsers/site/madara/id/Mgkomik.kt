package org.koitharu.kotatsu.parsers.site.madara.id

import okhttp3.Headers
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import java.util.*
import kotlin.random.Random

@MangaSourceParser("MGKOMIK", "MgKomik", "id")
internal class Mgkomik(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MGKOMIK, "mgkomik.id", 20) {

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}
		
	override val tagPrefix = "genres/"
	override val listUrl = "komik/"
	override val datePattern = "dd MMM yy"
	override val stylePage = ""
	override val sourceLocale: Locale = Locale.ENGLISH
	private val randomLength = Random.Default.nextInt(13, 21)
	private val randomString = generateRandomString(randomLength)
	override fun getRequestHeaders(): Headers = Headers.Builder()
		.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
		.add("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
		.add("Sec-Fetch-Dest", "document")
		.add("Sec-Fetch-Mode", "navigate")
		.add("Sec-Fetch-Site", "same-origin")
		.add("Sec-Fetch-User", "?1")
		.add("Upgrade-Insecure-Requests", "1")
		.add("X-Requested-With", randomString)
		.build()

	private fun generateRandomString(length: Int): String {
		val charset = "HALOGaES.BCDFHIJKMNPQRTUVWXYZ.bcdefghijklmnopqrstuvwxyz0123456789"
		return (1..length)
			.map { charset.random() }
			.joinToString("")
	}
}
