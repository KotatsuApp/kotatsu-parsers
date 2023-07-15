package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("BLOG_MANGA", "Blog Manga", "en")
internal class BlogManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BLOG_MANGA, "blogmanga.net") {

	override val postreq = true
}
