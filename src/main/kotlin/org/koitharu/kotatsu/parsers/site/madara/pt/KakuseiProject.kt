package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KAKUSEIPROJECT", "Kakusei Project", "pt")
internal class KakuseiProject(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KAKUSEIPROJECT, "kakuseiproject.com.br", 10)
