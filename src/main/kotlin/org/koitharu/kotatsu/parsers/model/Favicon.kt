package org.koitharu.kotatsu.parsers.model

import okhttp3.HttpUrl.Companion.toHttpUrl

public class Favicon(
	@JvmField public val url: String,
	@JvmField public val size: Int,
	@JvmField internal val rel: String?,
) : Comparable<Favicon> {

	@JvmField
	public val type: String = url.toHttpUrl().pathSegments.last()
		.substringAfterLast('.', "").lowercase()

	override fun compareTo(other: Favicon): Int {
		val res = size.compareTo(other.size)
		if (res != 0) {
			return res
		}
		return relWeightOf(rel).compareTo(relWeightOf(other.rel))
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Favicon

		if (url != other.url) return false
		if (size != other.size) return false
		if (rel != other.rel) return false

		return true
	}

	override fun hashCode(): Int {
		var result = url.hashCode()
		result = 31 * result + size
		result = 31 * result + rel.hashCode()
		return result
	}

	override fun toString(): String {
		return "Favicon(size=$size, type='$type', rel='$rel', url='$url')"
	}

	private fun relWeightOf(rel: String?) = when (rel) {
		"apple-touch-icon" -> 1 // Prefer apple-touch-icon because it has a better quality
		"mask-icon" -> -1
		else -> 0
	}
}
