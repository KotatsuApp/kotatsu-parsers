package org.koitharu.kotatsu.parsers.model

import androidx.collection.ArrayMap
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.util.findById

public class Manga(
	/**
	 * Unique identifier for manga
	 */
	@JvmField public val id: Long,
	/**
	 * Manga title, human-readable
	 */
	@JvmField public val title: String,
	/**
	 * Alternative title (for example on other language), may be null
	 */
	@JvmField public val altTitle: String?,
	/**
	 * Relative url to manga (**without** a domain) or any other uri.
	 * Used principally in parsers
	 */
	@JvmField public val url: String,
	/**
	 * Absolute url to manga, must be ready to open in browser
	 */
	@JvmField public val publicUrl: String,
	/**
	 * Normalized manga rating, must be in range of 0..1 or [RATING_UNKNOWN] if rating s unknown
	 * @see hasRating
	 */
	@JvmField public val rating: Float,
	/**
	 * Indicates that manga may contain sensitive information (18+, NSFW)
	 */
	@JvmField public val isNsfw: Boolean,
	/**
	 * Absolute link to the cover
	 * @see largeCoverUrl
	 */
	@JvmField public val coverUrl: String,
	/**
	 * Tags (genres) of the manga
	 */
	@JvmField public val tags: Set<MangaTag>,
	/**
	 * Manga status (ongoing, finished) or null if unknown
	 */
	@JvmField public val state: MangaState?,
	/**
	 * Author of the manga, may be null
	 */
	@JvmField public val author: String?,
	/**
	 * Large cover url (absolute), null if is no large cover
	 * @see coverUrl
	 */
	@JvmField public val largeCoverUrl: String? = null,
	/**
	 * Manga description, may be html or null
	 */
	@JvmField public val description: String? = null,
	/**
	 * List of chapters
	 */
	@JvmField public val chapters: List<MangaChapter>? = null,
	/**
	 * Manga source
	 */
	@JvmField public val source: MangaSource,
) {

	/**
	 * Return if manga has a specified rating
	 * @see rating
	 */
	public val hasRating: Boolean
		get() = rating > 0f && rating <= 1f

	public fun getChapters(branch: String?): List<MangaChapter> {
		return chapters?.filter { x -> x.branch == branch }.orEmpty()
	}

	public fun findChapterById(id: Long): MangaChapter? = chapters?.findById(id)

	public fun requireChapterById(id: Long): MangaChapter = requireNotNull(findChapterById(id)) {
		"Chapter with id $id not found"
	}

	public fun getBranches(): Map<String?, Int> {
		if (chapters.isNullOrEmpty()) {
			return emptyMap()
		}
		val result = ArrayMap<String?, Int>()
		chapters.forEach {
			val key = it.branch
			result[key] = result.getOrDefault(key, 0) + 1
		}
		return result
	}

	@InternalParsersApi
	public fun copy(
		url: String = this.url,
		title: String = this.title,
		altTitle: String? = this.altTitle,
		publicUrl: String = this.publicUrl,
		rating: Float = this.rating,
		isNsfw: Boolean = this.isNsfw,
		coverUrl: String = this.coverUrl,
		tags: Set<MangaTag> = this.tags,
		state: MangaState? = this.state,
		author: String? = this.author,
		largeCoverUrl: String? = this.largeCoverUrl,
		description: String? = this.description,
		chapters: List<MangaChapter>? = this.chapters,
		source: MangaSource = this.source,
	): Manga = Manga(
		id = id,
		title = title,
		altTitle = altTitle,
		url = url,
		publicUrl = publicUrl,
		rating = rating,
		isNsfw = isNsfw,
		coverUrl = coverUrl,
		tags = tags,
		state = state,
		author = author,
		largeCoverUrl = largeCoverUrl,
		description = description,
		chapters = chapters,
		source = source,
	)

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Manga

		if (id != other.id) return false
		if (title != other.title) return false
		if (altTitle != other.altTitle) return false
		if (url != other.url) return false
		if (publicUrl != other.publicUrl) return false
		if (rating != other.rating) return false
		if (isNsfw != other.isNsfw) return false
		if (coverUrl != other.coverUrl) return false
		if (tags != other.tags) return false
		if (state != other.state) return false
		if (author != other.author) return false
		if (largeCoverUrl != other.largeCoverUrl) return false
		if (description != other.description) return false
		if (chapters != other.chapters) return false
		if (source != other.source) return false

		return true
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + title.hashCode()
		result = 31 * result + (altTitle?.hashCode() ?: 0)
		result = 31 * result + url.hashCode()
		result = 31 * result + publicUrl.hashCode()
		result = 31 * result + rating.hashCode()
		result = 31 * result + isNsfw.hashCode()
		result = 31 * result + coverUrl.hashCode()
		result = 31 * result + tags.hashCode()
		result = 31 * result + (state?.hashCode() ?: 0)
		result = 31 * result + (author?.hashCode() ?: 0)
		result = 31 * result + (largeCoverUrl?.hashCode() ?: 0)
		result = 31 * result + (description?.hashCode() ?: 0)
		result = 31 * result + (chapters?.hashCode() ?: 0)
		result = 31 * result + source.hashCode()
		return result
	}

	override fun toString(): String {
		return "Manga($id - \"$title\" [$url] - $source)"
	}
}
