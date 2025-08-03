package org.koitharu.kotatsu.parsers.site.vi.yurigarden

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@MangaSourceParser("YURIGARDEN_R18", "Yuri Garden (18+)", "vi", type = ContentType.HENTAI)
internal class YuriGardenR18(context: MangaLoaderContext) :
	YuriGardenParser(
		context = context,
		source = MangaParserSource.YURIGARDEN_R18,
		domain = "yurigarden.com",
		isR18Enable = true
	)
