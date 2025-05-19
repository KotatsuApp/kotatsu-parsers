package org.koitharu.kotatsu.parsers.site.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.model.search.*
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.selectFirstOrThrow
import java.net.URL


@MangaSourceParser("HOLOEARTH", "HoloEarth", type = ContentType.MANGA)
internal class HoloEarthParser(context: MangaLoaderContext) : PagedMangaParser(context, MangaParserSource.HOLOEARTH, pageSize = 3) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("holoearth.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val availableSortOrders: Set<SortOrder> = setOf(SortOrder.NEWEST)

	override val searchQueryCapabilities: MangaSearchQueryCapabilities
		get() = MangaSearchQueryCapabilities()


	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()

	override suspend fun getListPage(query: MangaSearchQuery, page: Int): List<Manga> {
		val url = buildString {
			append("https://")
			append("holoearth.com")
			append("/en/alt/holonometria/manga")
		}

		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirstOrThrow(".manga__list")
		val mangaList = root.select("li .manga__item-inner")

		if (mangaList.isEmpty()) return emptyList();

		val mangas: List<Manga> = mangaList.mapNotNull { li ->
			val coverUrl = li.getElementsByTag("img").attr("src");
			val title = li.getElementsByClass("manga__title").text();
			val altTitle = li.getElementsByClass("manga__copy").text();
			val description = li.getElementsByClass("manga__caption").text();
			val publicUrl = li.getElementsByTag("a").attr("href");
			val relativeURL = URL(publicUrl).path

			Manga(
				id = generateUid(relativeURL),
				title = title,
				altTitles = setOf(altTitle),
				url = relativeURL,
				publicUrl = publicUrl,
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = coverUrl,
				tags = setOf<MangaTag>(),
				state = null,
				authors = setOf(),
				source = source,
				description = description,
			)
		}
		return mangas;
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val url = manga.publicUrl;
		val doc = webClient.httpGet(url).parseHtml()
		val root = doc.body().selectFirstOrThrow(".manga-detail__wrapper")
		val coverUrl = root.selectFirstOrThrow(".manga-detail__thumb img").attr("src");
		val chaptersList = root.selectFirstOrThrow(".manga-detail__list")
		val chapters = chaptersList.select("li")

		val mangaChapters = chapters.mapNotNull { li ->
			val url = li.selectFirstOrThrow("a").attr("href");
			val title = li.selectFirstOrThrow(".manga-detail__list-title").text().trim();

			MangaChapter(
				id = generateUid(url),
				title = title,
				number = 0F,
				volume = 0,
				url = url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			coverUrl = coverUrl,
			chapters = mangaChapters,
		)

	}


	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val url = chapter.url;
		val doc = webClient.httpGet(url).parseHtml()
		val imageList = doc.body().selectFirstOrThrow(".manga-detail__swiper-wrapper")
		val images = imageList.select(".manga-detail__swiper-slide").reversed();

		val mangaPages = images.mapNotNull {  page ->
			val img = page.selectFirst("img") ?: return@mapNotNull null
			val src = img.attr("src")
			MangaPage(
				id = generateUid(src),
				url = src,
				preview = src,
				source = source,
			)
		}
		return mangaPages;
	}

}
