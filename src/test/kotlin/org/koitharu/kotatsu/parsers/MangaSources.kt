package org.koitharu.kotatsu.parsers

import org.junit.jupiter.params.provider.EnumSource
import org.koitharu.kotatsu.parsers.model.MangaSource

@EnumSource(MangaSource::class, names = ["LOCAL", "DUMMY"], mode = EnumSource.Mode.EXCLUDE)
internal annotation class MangaSources