    package org.koitharu.kotatsu.parsers.site.liliana

    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import org.json.JSONObject
    import org.jsoup.nodes.Document
    import org.jsoup.nodes.Element
    import org.koitharu.kotatsu.parsers.MangaLoaderContext
    import org.koitharu.kotatsu.parsers.PagedMangaParser
    import org.koitharu.kotatsu.parsers.config.ConfigKey
    import org.koitharu.kotatsu.parsers.model.*
    import org.koitharu.kotatsu.parsers.util.*
    import java.text.SimpleDateFormat
    import org.jsoup.Jsoup
    import java.util.*

    internal abstract class LilianaParser(
        context: MangaLoaderContext,
        source: MangaParserSource,
        domain: String,
        pageSize: Int = 24
    ) : PagedMangaParser(context, source, pageSize) {

        override val configKeyDomain = ConfigKey.Domain(domain)

        override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
            super.onCreateConfig(keys)
            keys.add(userAgentKey)
        }

        override val availableSortOrders: Set<SortOrder> = EnumSet.of(
            SortOrder.UPDATED,
            SortOrder.POPULARITY,
            SortOrder.ALPHABETICAL,
            SortOrder.NEWEST,
            SortOrder.RATING_ASC
        )

        override val filterCapabilities: MangaListFilterCapabilities
            get() = MangaListFilterCapabilities(
                isMultipleTagsSupported = true,
                isSearchSupported = true,
                isSearchWithFiltersSupported = true
            )

        override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
            val url = buildString {
                append("https://")
                append(domain)
                when {
                    !filter.query.isNullOrEmpty() -> {
                        append("/search")
                        append("?keyword=")
                        append(filter.query.urlEncoded())
                    }
                    else -> {
                        append("/filter")
                    }
                }
                append("/")
                append(page)
                append("/")
                
                when (order) {
                    SortOrder.UPDATED -> append("?sort=latest-updated")
                    SortOrder.POPULARITY -> append("?sort=views")
                    SortOrder.ALPHABETICAL -> append("?sort=az")
                    SortOrder.NEWEST -> append("?sort=new")
                    SortOrder.RATING_ASC -> append("?sort=score")
                    else -> append("?sort=default")
                }
                
                filter.tags.forEach { tag ->
                    append("&genres=")
                    append(tag.key)
                }
                
                if (filter.states.isNotEmpty()) {
                    append("&status=")
                    append(when (filter.states.first()) {
                        MangaState.ONGOING -> "on-going"
                        MangaState.FINISHED -> "completed"
                        MangaState.PAUSED -> "on-hold"
                        MangaState.ABANDONED -> "canceled"
                        else -> "all"
                    })
                }
            }

            val doc = webClient.httpGet(url).parseHtml()
            return doc.select("div#main div.grid > div").map { parseSearchManga(it) }
        }

        private fun parseSearchManga(element: Element): Manga {
            val href = element.selectFirstOrThrow("a").attrAsRelativeUrl("href")
            return Manga(
                id = generateUid(href),
                url = href,
                publicUrl = href.toAbsoluteUrl(domain),
                coverUrl = element.selectFirst("img")?.src().orEmpty(),
                title = element.selectFirst(".text-center a")?.text().orEmpty(),
                altTitle = null,
                rating = RATING_UNKNOWN,
                tags = emptySet(),
                author = null,
                state = null,
                source = source,
                isNsfw = false,
            )
        }

        override suspend fun getDetails(manga: Manga): Manga {
            val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
            return manga.copy(
                description = doc.selectFirst("div#syn-target")?.text(),
                largeCoverUrl = doc.selectFirst(".a1 > figure img")?.src(),
                tags = doc.select(".a2 div > a[rel='tag'].label").mapToSet { a ->
                    MangaTag(
                        key = a.attr("href").substringAfterLast('/'),
                        title = a.text().toTitleCase(sourceLocale),
                        source = source,
                    )
                },
                author = doc.selectFirst("div.y6x11p i.fas.fa-user + span.dt")?.text()?.takeUnless {
                    it.equals("updating", true)
                },
                state = when (doc.selectFirst("div.y6x11p i.fas.fa-rss + span.dt")?.text()?.lowercase()) {
                    "on-going", "đang tiến hành", "進行中" -> MangaState.ONGOING
                    "completed", "hoàn thành", "完了" -> MangaState.FINISHED
                    "on-hold", "tạm dừng", "一時停止" -> MangaState.PAUSED
                    "canceled", "đã huỷ bỏ", "キャンセル" -> MangaState.ABANDONED
                    else -> null
                },
                chapters = doc.select("ul > li.chapter").mapChapters { i, element ->
                    val href = element.selectFirstOrThrow("a").attrAsRelativeUrl("href")
                    MangaChapter(
                        id = generateUid(href),
                        name = element.selectFirst("a")?.text().orEmpty(),
                        number = doc.select("ul > li.chapter").size - i.toFloat(),
                        url = href,
                        scanlator = null,
                        uploadDate = element.selectFirst("time[datetime]")?.attr("datetime")?.toLongOrNull()?.times(1000) ?: 0L,
                        branch = null,
                        source = source,
                        volume = 0
                    )
                }
            )
        }

        override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
            val fullUrl = chapter.url.toAbsoluteUrl(domain)
            val doc = webClient.httpGet(fullUrl).parseHtml()
            
            val script = doc.selectFirst("script:containsData(const CHAPTER_ID)")?.data()
                ?: throw Exception("Failed to get chapter id")

            val chapterId = script.substringAfter("const CHAPTER_ID = ").substringBefore(';')

            val ajaxUrl = buildString {
                append("https://")
                append(domain)
                append("/ajax/image/list/chap/")
                append(chapterId)
            }

            val responseJson = webClient.httpGet(ajaxUrl).parseJson()

            if (!responseJson.optBoolean("status", false)) {
                throw Exception(responseJson.optString("msg"))
            }

            val pageListHtml = responseJson.getString("html")
            val pageListDoc = Jsoup.parse(pageListHtml)

            return pageListDoc.select("div.iv-card").mapIndexed { index, div ->
                val img = div.selectFirst("img")
                val url = img?.attr("data-src") ?: img?.attr("src") ?: throw Exception("Failed to get image url")
                
                MangaPage(
                    id = generateUid(url),
                    url = url,
                    preview = null,
                    source = source,
                )
            }
        }

        protected open suspend fun getAvailableTags(): Set<MangaTag> = coroutineScope {
            val doc = webClient.httpGet("https://$domain/filter").parseHtml()
            doc.select("div.advanced-genres > div > .advance-item").mapToSet { element ->
                MangaTag(
                    key = element.selectFirstOrThrow("span[data-genre]").attr("data-genre"),
                    title = element.text().toTitleCase(sourceLocale),
                    source = source,
                )
            }
        }

        override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions(
            availableTags = getAvailableTags(),
            availableStates = setOf(MangaState.ONGOING, MangaState.FINISHED, MangaState.PAUSED, MangaState.ABANDONED)
        )
    }
