package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.*
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import java.util.*

@MangaSourceParser("CLONEMANGA", "CloneManga", "en")
internal class CloneMangaParser(override val context: MangaLoaderContext) : PagedMangaParser(
	MangaSource.CLONEMANGA,
	pageSize = 1,
) {

	override val sortOrders: Set<SortOrder> = Collections.singleton(
		SortOrder.POPULARITY,
	)

	override val configKeyDomain = ConfigKey.Domain("manga.clone-army.org", null)

	override fun getFaviconUrl(): String {
		return "https://pbs.twimg.com/profile_images/458758466346029056/Ys93EANp_400x400.png"
	}

	override suspend fun getListPage(
		page: Int,
		query: String?,
		tags: Set<MangaTag>?,
		sortOrder: SortOrder,
	): List<Manga> {
		if (query != null || page > 1) {
			return emptyList()
		}
		val link = "https://${getDomain()}/viewer_landing.php"
		val doc = context.httpGet(link).parseHtml()
		val mangas = doc.getElementsByClass("comicPreviewContainer")
		return mangas.mapNotNull { item ->
			val attr = item.getElementsByClass("comicPreview").attr("style")
			val href = item.selectFirst("a")?.attrAsAbsoluteUrl("href") ?: return@mapNotNull null
			val cover = attr.substring(attr.indexOf("site/themes"), attr.indexOf(")"))
			Manga(
				id = generateUid(href),
				title = item.selectFirst("h3")?.text() ?: return@mapNotNull null,
				coverUrl = "https://${getDomain()}/$cover",
				altTitle = null,
				author = "Dan Kim",
				rating = RATING_UNKNOWN,
				url = href,
				isNsfw = false,
				tags = emptySet(),
				state = null,
				publicUrl = href.toAbsoluteUrl(getDomain()),
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = context.httpGet(manga.publicUrl).parseHtml()
		val series = doc.location()
		val numChapters = Regex(
			pattern = "&page=(.*)&lang=",
		).findAll(
			input = doc.getElementsByTag("script")[3].toString(),
		)
			.elementAt(3).destructured.component1()
			.toInt()
		val chapters = ArrayList<MangaChapter>()
		for (i in 0..numChapters) {
			val chapter = MangaChapter(
				id = generateUid("$series&page=$i"),
				name = "Chapter ${i + 1}",
				number = i + 1,
				url = "$series&page=$i",
				scanlator = null,
				branch = null,
				uploadDate = 0L,
				source = MangaSource.DUMMY,
			)
			chapters.add(chapter)
		}
		return manga.copy(chapters = chapters)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = context.httpGet(chapter.url.toAbsoluteUrl(getDomain())).parseHtml()
		val imgUrl = doc.getElementsByClass("subsectionContainer")[0]
			.selectFirst("img")
			?.attrAsAbsoluteUrlOrNull("src") ?: doc.parseFailed("Something broken")
		return listOf(
			MangaPage(
				id = generateUid(imgUrl),
				url = imgUrl,
				referer = imgUrl,
				preview = null,
				source = MangaSource.DUMMY,
			),
		)
	}

	override suspend fun getTags(): Set<MangaTag> {
		return emptySet()
	}
}