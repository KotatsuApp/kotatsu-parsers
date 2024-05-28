package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("YURIMOONSUB", "Yurimoonsub.blogspot.com", "ar")
internal class YuriMoonSub(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.YURIMOONSUB, "yurimoonsub.blogspot.com")
