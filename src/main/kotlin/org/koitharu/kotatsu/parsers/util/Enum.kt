@file:JvmName("EnumUtils")

package org.koitharu.kotatsu.parsers.util

import kotlin.enums.EnumEntries

fun <E : Enum<E>> EnumEntries<E>.names() = Array(size) { i ->
	get(i).name
}

fun <E : Enum<E>> EnumEntries<E>.find(name: String): E? {
	return find { x -> x.name == name }
}
