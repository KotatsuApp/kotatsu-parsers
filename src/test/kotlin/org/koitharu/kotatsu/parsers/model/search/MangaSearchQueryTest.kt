package org.koitharu.kotatsu.parsers.model.search

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria.*
import org.koitharu.kotatsu.parsers.model.search.SearchableField.*
import java.util.Locale

class MangaSearchQueryCapabilitiesTest {

	private val capabilities = MangaSearchQueryCapabilities(
		capabilities = setOf(
			SearchCapability(TITLE_NAME, setOf(Match::class), multiValue = false, otherCriteria = false),
			SearchCapability(TAG, setOf(Include::class, Exclude::class), multiValue = true, otherCriteria = true),
			SearchCapability(PUBLICATION_YEAR, setOf(Range::class), multiValue = false, otherCriteria = true),
			SearchCapability(STATE, setOf(Include::class), multiValue = false, otherCriteria = true),
		)
	)

	@Test
	fun validateValidSingleCriterionQuery() {
		val query = MangaSearchQuery.builder()
			.criterion(Match(TITLE_NAME, "title"))
			.build()

		assertDoesNotThrow { capabilities.validate(query) }
	}

	@Test
	fun validateUnsupportedFieldThrowsException() {
		val query = MangaSearchQuery.builder()
			.criterion(Include(ORIGINAL_LANGUAGE, setOf(Locale.ENGLISH)))
			.build()

		assertThrows(IllegalArgumentException::class.java) { capabilities.validate(query) }
	}

	@Test
	fun validateUnsupportedMultiValueThrowsException() {
		val query = MangaSearchQuery.builder()
			.criterion(Include(STATE, setOf(MangaState.ONGOING, MangaState.FINISHED)))
			.build()

		assertThrows(IllegalArgumentException::class.java) { capabilities.validate(query) }
	}

	@Test
	fun validateMultipleCriteriaWithOtherCriteriaAllowed() {
		val query = MangaSearchQuery.builder()
			.criterion(Include(TAG, setOf(buildTag("tag1"), buildTag("tag2"))))
			.criterion(Exclude(TAG, setOf(buildTag("tag3"))))
			.build()

		assertDoesNotThrow { capabilities.validate(query) }
	}

	@Test
	fun validateMultipleCriteriaWithStrictCapabilityThrowsException() {
		val query = MangaSearchQuery.builder()
			.criterion(Match(TITLE_NAME, "title"))
			.criterion(Range(PUBLICATION_YEAR, 1990, 2000))
			.build()

		assertThrows(IllegalArgumentException::class.java) { capabilities.validate(query) }
	}

	private fun buildTag(name: String) = MangaTag(title = name, key = "${name}Key", source =  MangaParserSource.DUMMY)
}
