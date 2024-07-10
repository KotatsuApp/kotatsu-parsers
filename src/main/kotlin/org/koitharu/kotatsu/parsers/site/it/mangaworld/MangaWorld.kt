package org.koitharu.kotatsu.parsers.site.it.mangaworld

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("MANGAWORLD", "MangaWorld", "it")
internal class MangaWorld(
	context: MangaLoaderContext,
) : MangaWorldParser(context, MangaSource.MANGAWORLD, "mangaworld.ac")
