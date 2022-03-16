package org.koitharu.kotatsu.parsers.model

class Manga(
	val id: Long,
	val title: String,
	val altTitle: String?,
	val url: String, // relative url for internal use
	val publicUrl: String,
	val rating: Float, // normalized value [0..1] or -1
	val isNsfw: Boolean,
	val coverUrl: String,
	val tags: Set<MangaTag>,
	val state: MangaState?,
	val author: String?,
	val largeCoverUrl: String? = null,
	val description: String? = null, // HTML
	val chapters: List<MangaChapter>? = null,
	val source: MangaSource,
) {

	val hasRating: Boolean
		get() = rating in 0f..1f

	internal fun copy(
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
	) = Manga(
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
}