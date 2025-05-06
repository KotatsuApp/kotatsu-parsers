package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.*
import kotlin.time.Duration.Companion.minutes

internal class MangaParserSingleTest {

	private val testUnit = MangaParserTest()
	private val source = MangaParserSource.HENTAINEXUS
	private val timeout = 2.minutes

	@Test
	fun list() = runTest(timeout = timeout) {
		testUnit.list(source)
	}

	@Test
	fun pagination() = runTest(timeout = timeout) {
		testUnit.pagination(source)
	}

	@Test
	fun searchByTitleName() = runTest(timeout = timeout) {
		testUnit.searchByTitleName(source)
	}

	@Test
	fun tags() = runTest(timeout = timeout) {
		testUnit.tags(source)
	}

	@Test
	fun tagsMultiple() = runTest(timeout = timeout) {
		testUnit.tagsMultiple(source)
	}

	@Test
	fun locale() = runTest(timeout = timeout) {
		testUnit.locale(source)
	}


	@Test
	fun details() = runTest(timeout = timeout) {
		testUnit.details(source)
	}

	@Test
	fun pages() = runTest(timeout = timeout) {
		testUnit.pages(source)
	}

	@Test
	fun favicon() = runTest(timeout = timeout) {
		testUnit.favicon(source)
	}

	@Test
	fun domain() = runTest(timeout = timeout) {
		testUnit.domain(source)
	}

	@Test
	fun link() = runTest(timeout = timeout) {
		testUnit.link(source)
	}

	@Test
	@Disabled
	fun authorization() = runTest(timeout = timeout) {
		testUnit.authorization(source)
	}
}
