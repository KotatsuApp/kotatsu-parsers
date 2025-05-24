package org.koitharu.kotatsu.parsers.site.vi

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.LegacyPagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.*
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@MangaSourceParser("VCOMYCS", "Vcomycs", "vi", ContentType.MANGA)
internal class VcomycsParser(context: MangaLoaderContext) :
	LegacyPagedMangaParser(context, MangaParserSource.VCOMYCS, 36) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("vivicomi4.info")

	override val availableSortOrders: Set<SortOrder>
		get() = EnumSet.of(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions(
		availableTags = fetchTags(),
	)

	private suspend fun fetchTags(): Set<MangaTag> {
		return webClient.httpGet("/so-do-trang".toAbsoluteUrl(domain)).parseHtml()
			.selectFirstOrThrow(".sitemap-content .tags")
			.select("a")
			.mapToSet(::parseTag)
	}

	private fun parseTag(tagEl: Element): MangaTag {
		return MangaTag(
			title = tagEl.text().toTitleCase(),
			key = tagEl.attrAsRelativeUrl("href"),
			source = source,
		)
	}

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (!filter.query.isNullOrEmpty()) {
			if (page > 1) return emptyList()

			val payload = "action=searchtax&keyword=${filter.query.urlEncoded()}"
			return webClient.httpPost("/wp-admin/admin-ajax.php".toAbsoluteUrl(domain), payload)
				.parseJson().getJSONArray("data")
				.mapJSONNotNull { jo ->
					val status = jo.getString("cstatus")
					if (status == "Nhóm dịch" || status == "Tin tức") return@mapJSONNotNull null

					val relativeUrl = jo.getString("link").toRelativeUrl(domain)
					Manga(
						id = generateUid(relativeUrl),
						title = jo.getString("title"),
						altTitles = emptySet(),
						url = relativeUrl,
						publicUrl = relativeUrl.toAbsoluteUrl(domain),
						rating = RATING_UNKNOWN,
						contentRating = null,
						coverUrl = jo.getString("img"),
						tags = emptySet(),
						state = null,
						authors = emptySet(),
						largeCoverUrl = null,
						description = null,
						chapters = null,
						source = source,
					)
				}
		}

		val url = filter.tags.oneOrThrowIfMany()?.let { "${it.key}?page=$page" } ?: "/page/$page"
		val pageContent = webClient.httpGet(url.toAbsoluteUrl(domain)).parseHtml()
		if (pageContent.selectFirst(".pnf-404")?.text() == "Hết trang rồi!") {
			return emptyList()
		}

		return parseMangaList(pageContent)
	}

	private fun parseMangaList(page: Document): List<Manga> {
		return page.selectFirstOrThrow("div.comic-list")
			.select(".comic-img").map { item ->
				val linkEl = item.selectFirstOrThrow("a")
				val relativeUrl = linkEl.attrAsRelativeUrl("href")
				Manga(
					id = generateUid(relativeUrl),
					title = linkEl.attrOrThrow("title"),
					altTitles = emptySet(),
					url = relativeUrl,
					publicUrl = relativeUrl.toAbsoluteUrl(domain),
					rating = RATING_UNKNOWN,
					contentRating = null,
					coverUrl = linkEl.selectFirstOrThrow(".img-thumbnail").src(),
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					largeCoverUrl = null,
					description = null,
					chapters = null,
					source = source,
				)
			}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val content = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val info = content.selectFirstOrThrow(".comic-info")
		val author =
			info.selectFirst(".comic-intro-text > strong:contains(Tác giả:)")?.nextElementSibling()?.textOrNull()
		return manga.copy(
			rating = info.getElementById("cate-rating")?.let {
				val score = it.attrOrNull("data-score")?.toIntOrNull()
				val vote = it.attrOrNull("data-votes")?.toIntOrNull()
				if (score == null || vote == null || vote == 0) return@let null
				score / (vote * 10f)
			} ?: RATING_UNKNOWN,
			altTitles = setOfNotNull(
				info.selectFirst(".comic-intro-text > strong:contains(Tên khác:)")?.nextElementSibling()
					?.textOrNull(),
			),
			authors = setOfNotNull(author),
			state = when (info.selectFirst(".comic-stt")?.text()) {
				"Đang tiến hành" -> MangaState.ONGOING
				"Trọn bộ" -> MangaState.FINISHED
				else -> null
			},
			tags = info.select("div.tags > a").mapToSet(::parseTag),
			description = content.selectFirst(".intro-container > div.text-justify")?.let {
				it.selectFirst(".hide-long-text-shadow")?.remove()
				it.html()
			},
			contentRating = if (content.getElementById("adult-modal") != null) {
				ContentRating.ADULT
			} else {
				ContentRating.SAFE
			},
			chapters = content.select(".chapter-table .table-scroll tbody > tr a")
				.mapChapters(reversed = true) { index, element ->
					val url = element.attrAsRelativeUrl("href")
					MangaChapter(
						id = generateUid(url),
						title = element.selectFirst("span")?.textOrNull(),
						number = index + 1f,
						volume = 0,
						url = url,
						scanlator = null,
						uploadDate = 0L,
						branch = null,
						source = source,
					)
				},
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val encryptedContent = webClient.httpGet(chapter.url.toAbsoluteUrl(domain))
			.parseHtml()
			.selectFirstOrThrow("#view-chapter script").data()
			.substringAfter('\"')
			.substringBeforeLast('\"')
			.replace("\\\"", "\"")

		val images = decryptImages(encryptedContent)
		return Jsoup.parse(images).select("img").map { img ->
			val url = img.attrOrThrow("data-ehwufp")
				.replace("EhwuFp", ".")
				.replace("SJkhMV", ":")
				.replace("uUPzrw", "/")
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}
	}

	private fun decryptImages(secret: String): String {
		val json = JSONObject(secret)
		val salt = json.getString("salt").decodeHex()
		val iv = json.getString("iv").decodeHex()
		val cipherText = context.decodeBase64(json.getString("ciphertext"))

		val keySpec = PBEKeySpec("EhwuFpSJkhMVuUPzrw".toCharArray(), salt, 999, 256)
		val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(keySpec).encoded
		val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secretKey, "AES"), IvParameterSpec(iv))
		return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
	}

	override suspend fun getRelatedManga(seed: Manga): List<Manga> {
		val pageDocument = webClient.httpGet(seed.url.toAbsoluteUrl(domain)).parseHtml()
		return parseMangaList(pageDocument)
	}

	private fun String.decodeHex(): ByteArray {
		check(length % 2 == 0) { "Must have an even length" }

		return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
	}
}
