package org.koitharu.kotatsu.parsers

import org.junit.jupiter.params.provider.EnumSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@EnumSource(MangaParserSource::class)
internal annotation class MangaSources
