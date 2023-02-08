package org.koitharu.kotatsu.parsers.site.madara

import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.attrAsAbsoluteUrlOrNull
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import java.util.*

@MangaSourceParser("TATAKAE_SCANS", "Tatakae Scans", "pt")
internal class TatakaeScansParser(context: MangaLoaderContext) :
    Madara6Parser(context, MangaSource.TATAKAE_SCANS, "tatakaescan.com") {

    override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"

    override fun parseDetails(manga: Manga, body: Element, chapters: List<MangaChapter>): Manga {
        val root = body.selectFirstOrThrow(".site-content")
        val postContent = root.selectFirstOrThrow(".post-content")
        val tags = postContent.getElementsContainingOwnText("Gênero")
            .firstOrNull()?.tableValue()
            ?.getElementsByAttributeValueContaining("href", tagPrefix)
            ?.mapToSet { a -> a.asMangaTag() } ?: manga.tags
        return manga.copy(
            largeCoverUrl = root.selectFirst("picture")
                ?.selectFirst("img[data-src]")
                ?.attrAsAbsoluteUrlOrNull("data-src"),
            description = (root.selectFirst(".detail-content")
                ?: root.selectFirstOrThrow(".manga-excerpt")).html(),
            author = postContent.getElementsContainingOwnText("Autor")
                .firstOrNull()?.tableValue()?.text()?.trim(),
            state = postContent.getElementsContainingOwnText("Status")
                .firstOrNull()?.tableValue()?.text()?.asMangaState(),
            tags = tags,
            isNsfw = body.hasClass("adult-content"),
            chapters = chapters,
        )
    }

    override fun String.asMangaState() = when (trim().lowercase(Locale.ROOT)) {
        "em lançamento" -> MangaState.ONGOING

        else -> null
    }

    override fun getFaviconUrl(): String {
        return "https://${domain}/wp-content/uploads/2022/07/cropped-favicon-180x180.png"
    }

}
