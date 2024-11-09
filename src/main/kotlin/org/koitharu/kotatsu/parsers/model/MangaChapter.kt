package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.util.formatSimple

public class MangaChapter(
	/**
	 * An unique id of chapter
	 */
	@JvmField public val id: Long,
	/**
	 * User-readable name of chapter
	 */
	@JvmField public val name: String,
	/**
	 * Chapter number starting from 1, 0 if unknown
	 */
	@JvmField public val number: Float,
	/**
	 * Volume number starting from 1, 0 if unknown
	 */
	@JvmField public val volume: Int,
	/**
	 * Relative url to chapter (**without** a domain) or any other uri.
	 * Used principally in parsers
	 */
	@JvmField public val url: String,
	/**
	 * User-readable name of scanlator (releaser) or null if unknown
	 */
	@JvmField public val scanlator: String?,
	/**
	 * Chapter upload date in milliseconds
	 */
	@JvmField public val uploadDate: Long,
	/**
	 * User-readable name of branch.
	 * A branch is a group of chapters that overlap (e.g. different languages)
	 */
	@JvmField public val branch: String?,
	@JvmField public val source: MangaSource,
) {

	public fun numberString(): String? = if (number > 0f) {
		number.formatSimple()
	} else {
		null
	}

	public fun volumeString(): String? = if (volume > 0) {
		volume.toString()
	} else {
		null
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MangaChapter

		if (id != other.id) return false
		if (name != other.name) return false
		if (number != other.number) return false
		if (volume != other.volume) return false
		if (url != other.url) return false
		if (scanlator != other.scanlator) return false
		if (uploadDate != other.uploadDate) return false
		if (branch != other.branch) return false
		if (source != other.source) return false

		return true
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + name.hashCode()
		result = 31 * result + number.hashCode()
		result = 31 * result + volume
		result = 31 * result + url.hashCode()
		result = 31 * result + (scanlator?.hashCode() ?: 0)
		result = 31 * result + uploadDate.hashCode()
		result = 31 * result + (branch?.hashCode() ?: 0)
		result = 31 * result + source.hashCode()
		return result
	}

	override fun toString(): String {
		return "MangaChapter($id - #$number [$url] - $source)"
	}

	internal fun copy(volume: Int, number: Float) = MangaChapter(
		id = id,
		name = name,
		number = number,
		volume = volume,
		url = url,
		scanlator = scanlator,
		uploadDate = uploadDate,
		branch = branch,
		source = source,
	)
}
