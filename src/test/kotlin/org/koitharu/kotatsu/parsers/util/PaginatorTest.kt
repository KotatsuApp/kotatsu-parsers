package org.koitharu.kotatsu.parsers.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PaginatorTest {

	@Test
	fun singlePaginationTest() {
		val paginator = Paginator(24)
		assertEquals(1, paginator.getPage(0))
		assertEquals(2, paginator.getPage(24))
		assertEquals(3, paginator.getPage(48))
	}

	@Test
	fun adaptivePaginationTest() {
		val paginator = Paginator(12)
		assertEquals(1, paginator.getPage(0))
		paginator.onListReceived(0, 1, 24)
		assertEquals(2, paginator.getPage(24))
		paginator.onListReceived(24, 2, 18)
		assertEquals(3, paginator.getPage(42))
	}

	@Test
	fun endReachPaginationTest() {
		val pageSize = 24
		val paginator = Paginator(pageSize)
		var size = 0
		repeat(5) { i ->
			val offset = i * pageSize
			assertEquals(i + 1, paginator.getPage(offset))
			paginator.onListReceived(offset, i + 1, pageSize)
			size += pageSize
		}
		val nextPage = paginator.getPage(size)
		assertEquals(nextPage, paginator.getPage(size))
		paginator.onListReceived(size, nextPage, 0)
		assertEquals(nextPage, paginator.getPage(size))
	}
}