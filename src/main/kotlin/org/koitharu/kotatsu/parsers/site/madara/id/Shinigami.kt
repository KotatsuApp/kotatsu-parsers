package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("SHINIGAMI", "Shinigami", "id")
internal class Shinigami(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SHINIGAMI, "shinigamitoon.com", 10) {

	private val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_MOBILE)
	override val tagPrefix = "genre/"
	override val listUrl = "series/"
	override val sourceLocale: Locale = Locale.ENGLISH

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

}
