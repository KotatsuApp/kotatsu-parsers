package org.koitharu.kotatsu.parsers.site.vi.yurigarden

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@MangaSourceParser("YURIGARDEN", "Yuri Garden", "vi")
internal class YuriGarden(context: MangaLoaderContext) :
	YuriGardenParser(
		context = context,
		source = MangaParserSource.YURIGARDEN,
		domain = "yurigarden.com",
		isR18Enable = false
	)
