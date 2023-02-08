package org.koitharu.kotatsu.parsers.site.grouple

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("MINTMANGA", "MintManga", "ru")
internal class MintMangaParser(
    context: MangaLoaderContext,
) : GroupleParser(context, MangaSource.MINTMANGA, "mintmangafun", 2) {

    override val configKeyDomain = ConfigKey.Domain(
        "mintmanga.live",
        arrayOf("mintmanga.live", "mintmanga.com"),
    )

    override fun getFaviconUrl(): String {
        return "https://resmm.rmr.rocks/static/apple-touch-icon-8fff291039c140493adb0c7ba81065ad.png"
    }
}
