package org.koitharu.kotatsu.parsers.site.zh

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.json.mapJSON
import org.koitharu.kotatsu.parsers.util.json.mapJSONIndexed
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.parsers.util.getCookies
import java.util.*
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.network.UserAgents
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.util.mimeType
import org.koitharu.kotatsu.parsers.util.parseRaw
import org.koitharu.kotatsu.parsers.util.parseHtml
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Request
import kotlinx.coroutines.delay
import org.koitharu.kotatsu.parsers.util.json.mapJSONNotNullToSet
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

@MangaSourceParser("KOMIIC", "Komiic", "zh")
internal class KomiicParser(context: MangaLoaderContext) :
    PagedMangaParser(context, MangaParserSource.KOMIIC, pageSize = 20) {

    override val configKeyDomain = ConfigKey.Domain("komiic.com")

    // 使用桌面版 UA，降低被拦截概率
    override val userAgentKey = ConfigKey.UserAgent(UserAgents.CHROME_DESKTOP)

    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
        super.onCreateConfig(keys)
        keys.add(userAgentKey)
    }

    override val availableSortOrders: Set<SortOrder> = EnumSet.of(
        SortOrder.UPDATED,
        SortOrder.POPULARITY_MONTH,
        SortOrder.POPULARITY,
    )

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isSearchWithFiltersSupported = true,
            isMultipleTagsSupported = true,
        )

    // 简单的第一页列表缓存，返回上次已加载的数据以避免返回时闪烁刷新
    @Volatile
    private var recentFirstPageCache: List<Manga>? = null

    @Volatile
    private var recentFirstPageCacheLevel: Int? = null

    @Volatile
    private var searchFirstPageCacheQuery: String? = null

    @Volatile
    private var searchFirstPageCache: List<Manga>? = null

    // 为图片请求补充必要的头（主要是 Referer），避免部分服务端拒绝
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val needsImageHeaders = req.url.host.equals(domain, ignoreCase = true)
            && req.url.encodedPath.startsWith("/api/image/")
        return if (needsImageHeaders) {
            // 参考 venera-configs/komiic.js 的 onImageLoad：从 URL 片段还原精准 Referer
            val fragment = req.url.fragment
            val referer = if (!fragment.isNullOrEmpty() && fragment.contains("comic=") && fragment.contains("ep=")) {
                val comicId = fragment.substringAfter("comic=").substringBefore('&')
                val epId = fragment.substringAfter("ep=").substringBefore('&')
                if (comicId.isNotEmpty() && epId.isNotEmpty()) {
                    "https://$domain/comic/$comicId/chapter/$epId/images/all"
                } else {
                    "https://$domain/"
                }
            } else {
                "https://$domain/"
            }

            val newReq = req.newBuilder()
                .header("Accept", "image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8")
                .header("User-Agent", UserAgents.CHROME_DESKTOP)
                .header("Referer", referer)
                .header("Origin", "https://$domain")
                // 不在图片请求上强行附加 Authorization，避免服务端返回 400
                .removeHeader("Authorization")
                .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(req)
        }
    }

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        // 分类只保留站点的固定分类，不混入色气程度标签
        val tags = fetchAvailableTags()
        return MangaListFilterOptions(
            availableTags = tags,
            availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
            availableContentRating = EnumSet.of(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT),
        )
    }

    override fun getRequestHeaders(): Headers = super.getRequestHeaders().newBuilder()
        .add("Referer", "https://$domain/")
        .add("Origin", "https://$domain")
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Sec-Fetch-Site", "same-origin")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Dest", "empty")
        .add("Content-Type", "application/json")
        .apply {
            // 若存在登录产生的 token 或 access_token Cookie，附加 Bearer 认证头
            val cookies = context.cookieJar.getCookies(domain)
            val tokenCookie: okhttp3.Cookie? = cookies.firstOrNull { c ->
                c.name.equals("token", true) || c.name.equals("access_token", true)
            }
            if (tokenCookie != null) {
                val v = tokenCookie.value
                if (v.isNotEmpty()) {
                    // 使用通用 Bearer 方案，部分接口可能更兼容
                    add("Authorization", "Bearer $v")
                }
            }
        }
        .build()

    override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val offset = (page - paginator.firstPage) * pageSize
        val sexyLevel = parseSexyLevel(filter)
        // 严格使用原始查询：始终将 sexyLevel 传递给远端（若未选择则为 null）
        val remoteSexyLevel: Int? = sexyLevel
        val statusParam = when {
            filter.states.contains(MangaState.ONGOING) && !filter.states.contains(MangaState.FINISHED) -> "ONGOING"
            filter.states.contains(MangaState.FINISHED) && !filter.states.contains(MangaState.ONGOING) -> "END"
            else -> ""
        }
        val orderByParam = when (order) {
            SortOrder.UPDATED -> "DATE_UPDATED"
            SortOrder.POPULARITY_MONTH -> "MONTH_VIEWS"
            SortOrder.POPULARITY -> "VIEWS"
            else -> "DATE_UPDATED"
        }

        val base = if (!filter.query.isNullOrEmpty()) {
            val list = search(filter.query!!)
            if (page == paginator.firstPage) {
                searchFirstPageCacheQuery = filter.query
                searchFirstPageCache = list
            }
            list
        } else if (filter.tags.isNotEmpty()) {
            val categoryIds = filter.tags.map { it.key }.filter { it.matches(Regex("^\\d+")) }
            if (categoryIds.isNotEmpty()) {
                listByCategories(
                    categoryIds,
                    offset,
                    orderByParam,
                    statusParam,
                    asc = false,
                    sexyLevel = remoteSexyLevel,
                )
            } else {
                when (orderByParam) {
                    "DATE_UPDATED" -> {
                        if (
                            page == paginator.firstPage &&
                            statusParam.isEmpty() &&
                            recentFirstPageCache != null &&
                            recentFirstPageCacheLevel == remoteSexyLevel
                        ) {
                            recentFirstPageCache!!
                        } else {
                            val list = recentUpdate(offset, statusParam, remoteSexyLevel)
                            if (page == paginator.firstPage && statusParam.isEmpty()) {
                                recentFirstPageCache = list
                                recentFirstPageCacheLevel = remoteSexyLevel
                            }
                            list
                        }
                    }

                    else -> hotComics(offset, orderByParam, statusParam, remoteSexyLevel)
                }
            }
        } else {
            when (orderByParam) {
                "DATE_UPDATED" -> {
                    if (
                        page == paginator.firstPage &&
                        statusParam.isEmpty() &&
                        recentFirstPageCache != null &&
                        recentFirstPageCacheLevel == remoteSexyLevel
                    ) {
                        recentFirstPageCache!!
                    } else {
                        val list = recentUpdate(offset, statusParam, remoteSexyLevel)
                        if (page == paginator.firstPage && statusParam.isEmpty()) {
                            recentFirstPageCache = list
                            recentFirstPageCacheLevel = remoteSexyLevel
                        }
                        list
                    }
                }

                else -> hotComics(offset, orderByParam, statusParam, remoteSexyLevel)
            }
        }

        return applyLocalFilters(base, filter)
    }

    private suspend fun recentUpdate(offset: Int, status: String = "", sexyLevel: Int? = null): List<Manga> {
        val query = """
	query recentUpdate(${'$'}pagination: Pagination!) {
		recentUpdate(pagination: ${'$'}pagination) {
			id
			title
			status
			year
			imageUrl
			authors { id name __typename }
			categories { id name __typename }
			dateUpdated
			monthViews
			views
			favoriteCount
			lastBookUpdate
			lastChapterUpdate
			__typename
		}
	}
	""".trimIndent()
        val variables = JSONObject().apply {
            put(
                "pagination",
                JSONObject().apply {
                    put("limit", pageSize)
                    put("offset", offset)
                    put("orderBy", "DATE_UPDATED")
                    put("status", status)
                    put("asc", true)
                    if (sexyLevel != null) put("sexyLevel", sexyLevel)
                },
            )
        }
        val data = apiCall(query, "recentUpdate", variables)
        val arr: JSONArray = data.optJSONArray("recentUpdate") ?: JSONArray()
        return arr.toMangaList()
    }

    private suspend fun hotComics(offset: Int, orderBy: String, status: String, sexyLevel: Int? = null): List<Manga> {
        val query = """
	query hotComics(${'$'}pagination: Pagination!) {
		hotComics(pagination: ${'$'}pagination) {
			id
			title
			status
			year
			imageUrl
			authors { id name __typename }
			categories { id name __typename }
			dateUpdated
			monthViews
			views
			favoriteCount
			lastBookUpdate
			lastChapterUpdate
			__typename
		}
	}
	""".trimIndent()
        val variables = JSONObject().apply {
            put(
                "pagination",
                JSONObject().apply {
                    put("limit", pageSize)
                    put("offset", offset)
                    put("orderBy", orderBy)
                    put("status", status)
                    put("asc", true)
                    if (sexyLevel != null) put("sexyLevel", sexyLevel)
                },
            )
        }
        val data = apiCall(query, "hotComics", variables)
        val arr: JSONArray = data.optJSONArray("hotComics") ?: JSONArray()
        return arr.toMangaList()
    }

    private suspend fun listByCategories(
        categoryIds: List<String>,
        offset: Int,
        orderBy: String,
        status: String,
        asc: Boolean,
        sexyLevel: Int? = null,
    ): List<Manga> {
        val query = """
	query comicByCategories(${'$'}categoryId: [ID!]!, ${'$'}pagination: Pagination!) {
		comicByCategories(categoryId: ${'$'}categoryId, pagination: ${'$'}pagination) {
			id
			title
			status
			year
			imageUrl
			authors { id name __typename }
			categories { id name __typename }
			dateUpdated
			monthViews
			views
			favoriteCount
			lastBookUpdate
			lastChapterUpdate
			__typename
		}
	}
	""".trimIndent()
        val variables = JSONObject().apply {
            put(
                "categoryId",
                JSONArray().apply {
                    categoryIds.forEach { put(it) }
                },
            )
            put(
                "pagination",
                JSONObject().apply {
                    put("limit", pageSize)
                    put("offset", offset)
                    put("orderBy", orderBy)
                    put("status", status)
                    put("asc", asc)
                    if (sexyLevel != null) put("sexyLevel", sexyLevel)
                },
            )
        }
        val data = apiCall(query, "comicByCategories", variables)
        val arr: JSONArray = data.optJSONArray("comicByCategories") ?: JSONArray()
        return arr.toMangaList()
    }

    private suspend fun search(query: String): List<Manga> {
        val request = """
	query searchComicAndAuthorQuery(${'$'}keyword: String!) {
		searchComicsAndAuthors(keyword: ${'$'}keyword) {
			comics {
				id
				title
				status
				year
				imageUrl
				authors { id name __typename }
				categories { id name __typename }
				dateUpdated
				monthViews
				views
				favoriteCount
				lastBookUpdate
				lastChapterUpdate
				__typename
			}
			authors { id name chName enName wikiLink comicCount views __typename }
			__typename
		}
	}
	""".trimIndent()
        val variables = JSONObject().apply { put("keyword", query) }
        val data = apiCall(request, "searchComicAndAuthorQuery", variables)
        val parent = data.optJSONObject("searchComicsAndAuthors")
        val arr = parent?.optJSONArray("comics") ?: JSONArray()
        return arr.toMangaList()
    }

    private fun JSONArray.toMangaList(): List<Manga> = mapJSON { jo ->
        val id = jo.optString("id")
        val title = jo.optString("title")
        val cover = jo.optString("imageUrl", null)
        val status = jo.optString("status", null)
        val state = when (status) {
            "END", "FINISHED", "finished" -> MangaState.FINISHED
            "ONGOING", "ongoing" -> MangaState.ONGOING
            else -> null
        }
        val authors = jo.optJSONArray("authors")?.mapJSONNotNullToSet { a ->
            a.optString("name").takeIf { it.isNotEmpty() }
        }.orEmpty()
        val catNames = jo.optJSONArray("categories")?.mapJSONNotNullToSet { c ->
            c.optString("name").takeIf { it.isNotEmpty() }
        }.orEmpty()
        val catIds = jo.optJSONArray("categories")?.mapJSONNotNullToSet { c ->
            c.optString("id").takeIf { it.isNotEmpty() }
        }.orEmpty()
        val tags = catNames.zip(catIds.ifEmpty { catNames }).mapToSet { (n, k) ->
            MangaTag(title = n, key = k, source = source)
        }
        val rating = guessContentRating(catNames)
        Manga(
            id = generateUid(id.ifEmpty { title }),
            title = title,
            altTitles = emptySet(),
            coverUrl = cover,
            largeCoverUrl = cover,
            authors = authors,
            contentRating = rating,
            rating = RATING_UNKNOWN,
            url = id,
            publicUrl = "/comic/$id".toAbsoluteUrl(domain),
            tags = tags,
            state = state,
            source = source,
        )
    }

    private suspend fun applyLocalFilters(list: List<Manga>, filter: MangaListFilter): List<Manga> {
        var result = list
        // 仅保留状态与排除标签的本地过滤；色气程度严格依赖远端 sexyLevel
        if (filter.states.isNotEmpty()) {
            result = result.filter { m -> m.state != null && filter.states.contains(m.state) }
        }
        if (filter.tagsExclude.isNotEmpty()) {
            val excludeKeys = filter.tagsExclude.mapToSet { it.key }
            result = result.filter { m -> m.tags.none { t -> excludeKeys.contains(t.key) } }
        }
        return result
    }

    private fun parseSexyLevel(filter: MangaListFilter): Int? {
        // 将“内容分级”映射到色气程度阈值：SAFE -> 0, SUGGESTIVE -> 1, ADULT -> 4
        var max: Int? = null
        if (filter.contentRating.isNotEmpty()) {
            filter.contentRating.forEach { r ->
                val v = when (r) {
                    ContentRating.SAFE -> 0
                    ContentRating.SUGGESTIVE -> 1
                    ContentRating.ADULT -> 4
                }
                max = if (max == null) v else kotlin.math.max(max, v)
            }
        }
        return max
    }

    private fun guessContentRating(tagNames: Set<String>): ContentRating? {
        val lower = tagNames.mapToSet { it.lowercase(Locale.ROOT) }
        val adultKeys = setOf("成人", "限制", "r18", "情色", "nsfw", "十八禁")
        val suggestiveKeys = setOf("後宮", "福利", "性感", "誘惑", "擦邊", "肉番")
        return when {
            lower.any { s -> adultKeys.any { s.contains(it) } } -> ContentRating.ADULT
            lower.any { s -> suggestiveKeys.any { s.contains(it) } } -> ContentRating.SUGGESTIVE
            else -> ContentRating.SAFE
        }
    }

    @Volatile
    private var cachedTags: Set<MangaTag>? = null

    private fun fetchAvailableTags(): Set<MangaTag> {
        val cached = cachedTags
        if (cached != null) return cached
        // 以站点固定分类为基准（来源于官方前端配置），避免采样不全
        val names = arrayOf(
            "愛情",
            "神鬼",
            "校園",
            "搞笑",
            "生活",
            "懸疑",
            "冒險",
            "職場",
            "魔幻",
            "後宮",
            "魔法",
            "格鬥",
            "宅男",
            "勵志",
            "耽美",
            "科幻",
            "百合",
            "治癒",
            "萌系",
            "熱血",
            "競技",
            "推理",
            "雜誌",
            "偵探",
            "偽娘",
            "美食",
            "恐怖",
            "四格",
            "社會",
            "歷史",
            "戰爭",
            "舞蹈",
            "武俠",
            "機戰",
            "音樂",
            "體育",
            "黑道",
        )
        val ids = arrayOf(
            "1",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "10",
            "11",
            "2",
            "12",
            "13",
            "14",
            "15",
            "16",
            "17",
            "18",
            "19",
            "20",
            "21",
            "22",
            "23",
            "24",
            "25",
            "26",
            "27",
            "9",
            "28",
            "31",
            "32",
            "33",
            "34",
            "35",
            "36",
            "37",
            "40",
            "42",
        )
        val set = names.zip(ids).mapToSet { (n, k) -> MangaTag(title = n, key = k, source = source) }
        cachedTags = set
        return set
    }

    // 已移除 lewd-* 标签定义，统一通过“内容分级”进行色气程度选择

    @Volatile
    private var lewdnessCache: MutableMap<String, Int> = mutableMapOf()

    private suspend fun fetchLewdnessLevel(comicId: String): Int? {
        val cached = lewdnessCache[comicId]
        if (cached != null) return cached
        // 优先尝试 GraphQL 详情的 sexyLevel 字段（更稳定）
        runCatchingCancellable {
            val q = """
            query comicById(${'$'}id: ID!) {
                comicById(comicId: ${'$'}id) { id sexyLevel __typename }
            }
            """.trimIndent()
            val variables = JSONObject().apply { put("id", comicId) }
            val data = apiCall(q, "comicById", variables)
            val obj = data.optJSONObject("comicById")
            val lv = obj?.optInt("sexyLevel")
            if (lv != null) {
                lewdnessCache[comicId] = lv
                return lv
            }
        }.getOrElse { /* ignore and fallback to HTML */ }
        val doc = try {
            webClient.httpGet("https://$domain/comic/$comicId", getRequestHeaders()).parseHtml()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        // 优先从表单控件读取：input/select[name="sexyLevel"] 的值
        var valueText: String? = doc.select("input[name=sexyLevel]").firstOrNull()?.attr("value")
        if (valueText.isNullOrEmpty()) {
            val sel = doc.select("select[name=sexyLevel]").firstOrNull()
            if (sel != null) {
                val opt = sel.select("option[selected]").firstOrNull()
                if (opt != null) {
                    valueText = opt.attr("value").ifEmpty { opt.text() }
                }
            }
        }

        var level: Int? = valueText?.trim()?.toIntOrNull()
        if (level == null) {
            // 回退到文本解析（兼容显示文案变化）
            val text = doc.text()
            val re = Regex("色[气氣]程度\\s*[:：]?\\s*(无|>=\\s*4|[0-9]+)")
            val m = re.find(text) ?: run {
                Regex("(Lewdness|R18)\\s*[:：]?\\s*(None|>=\\s*4|[0-9]+)", RegexOption.IGNORE_CASE).find(text)
            }
            level = when (m?.groupValues?.getOrNull(1)?.trim()?.lowercase(Locale.ROOT)) {
                "无", "none" -> 0
                ">=4" -> 4
                null -> null
                else -> m.groupValues[1].trim().toIntOrNull() ?: 0
            }
        }
        if (level != null) {
            lewdnessCache[comicId] = level
        }
        return level
    }

    override suspend fun getDetails(manga: Manga): Manga {
        val request = """
        query comicById(${'$'}id: ID!) {
            comicById(comicId: ${'$'}id) {
                id
                title
                description
                imageUrl
                status
            }
        }
        """.trimIndent()
        val variables = JSONObject().apply { put("id", manga.url) }
        val data = apiCall(request, "comicById", variables)
        val obj: JSONObject = data.optJSONObject("comicById") ?: return manga
        val title = obj.optString("title", manga.title)
        val cover = obj.optString("imageUrl", manga.coverUrl)
        val status = obj.optString("status", null)
        val state = when (status) {
            "END", "FINISHED", "finished" -> MangaState.FINISHED
            "ONGOING", "ongoing" -> MangaState.ONGOING
            else -> manga.state
        }

        // 拉取章节列表（与详情分离，避免 Schema 不支持嵌套 chapters）
        val qCh = """
        query chaptersByComicId(${'$'}comicId: ID!) {
            chaptersByComicId(comicId: ${'$'}comicId) {
                id
                serial
                type
                size
                dateUpdated
                __typename
            }
        }
        """.trimIndent()
        val variablesCh = JSONObject().apply { put("comicId", manga.url) }
        val dataCh = apiCall(qCh, "chaptersByComicId", variablesCh)
        val chaptersJson = dataCh.optJSONArray("chaptersByComicId") ?: JSONArray()
        val chapters = chaptersJson.mapJSONIndexed { i, jo ->
            val chId = jo.optString("id", "${manga.url}-$i")
            val serialStr = jo.optString("serial", (i + 1).toString())
            val number = serialStr.toFloatOrNull() ?: (i + 1).toFloat()
            val dateStr = jo.optString("dateUpdated", "")
            val upload = if (dateStr.isNotEmpty()) runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(dateStr)?.time ?: 0L
            }.getOrDefault(0L) else 0L
            MangaChapter(
                id = generateUid(chId),
                title = "第${serialStr}话",
                number = number,
                volume = 0,
                url = chId, // use chapter id for API queries
                scanlator = null,
                uploadDate = upload,
                branch = manga.url, // 传递 comicId，供图片 Referer 还原
                source = source,
            )
        }.sortedBy { it.number }

        return manga.copy(
            title = title,
            coverUrl = cover,
            largeCoverUrl = cover,
            description = obj.optString("description", manga.description),
            state = state,
            chapters = chapters,
        )
    }

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val chapterId = chapter.url.substringAfterLast('/')
        val comicId = chapter.branch
        // 预热图片页面，促使站点生成必要状态/缓存，减少首次为空
        if (!comicId.isNullOrEmpty()) {
            runCatchingCancellable {
                webClient.httpGet("https://$domain/comic/$comicId/chapter/$chapterId/images/all", getRequestHeaders())
                    .close()
            }
        }
        // 优先使用 imagesByChapterId，若返回 kid，则拼接 /api/image/{kid}
        val q1 = """
        query imagesByChapterId(${'$'}chapterId: ID!) {
            imagesByChapterId(chapterId: ${'$'}chapterId) {
                id
                kid
                height
                width
                __typename
            }
        }
        """.trimIndent()
        val variables1 = JSONObject().apply { put("chapterId", chapterId) }
        var data1 = apiCall(q1, "imagesByChapterId", variables1)
        // 仅使用 imagesByChapterId 生成页面，避免对 chapterById 的必需参数错误
        var imagesArray: JSONArray = data1.optJSONArray("imagesByChapterId") ?: JSONArray()

        // 首次进入偶发空列表（站点 Cookie/验证尚未就绪），引入最多 3 次重试（逐步退避）避免误报“无章节”
        if (imagesArray.length() == 0) {
            val delays = intArrayOf(300, 700, 1200)
            for (d in delays) {
                delay(d.toLong())
                data1 = apiCall(q1, "imagesByChapterId", variables1)
                imagesArray = data1.optJSONArray("imagesByChapterId") ?: imagesArray
                if (imagesArray.length() > 0) break
            }
        }

        val pages = ArrayList<MangaPage>(imagesArray.length())
        for (i in 0 until imagesArray.length()) {
            val jo = imagesArray.optJSONObject(i) ?: continue
            val kid = jo.optString("kid", null)
            val img = if (!kid.isNullOrEmpty()) "https://$domain/api/image/$kid" else null
            if (img.isNullOrEmpty()) continue
            val fragment = if (!comicId.isNullOrEmpty()) "comic=$comicId&ep=$chapterId" else null
            val finalUrl = if (fragment != null) "$img#$fragment" else img
            pages += MangaPage(
                id = generateUid("${chapter.url}/$i"),
                url = finalUrl,
                preview = null,
                source = source,
            )
        }

        // 为避免首次进入时图片接口尚未就绪导致瞬时 404，
        // 对第一页图片执行就绪预检（HEAD/GET）并做小步退避重试。
        // 这会在返回页面列表前确保至少第一张图可用，从而不触发“无章节或已删除”。
        if (pages.isNotEmpty()) {
            val firstUrl = pages[0].url
            val retryDelays = intArrayOf(200, 500, 900)
            var ready = false
            // 尝试 HEAD 以轻量预检（由 intercept 注入必要 Referer）
            for ((idx, d) in retryDelays.withIndex()) {
                // 间隔退避
                delay(d.toLong())
                val ok = runCatchingCancellable {
                    webClient.httpHead(firstUrl).close()
                    true
                }.getOrDefault(false)
                if (ok) {
                    ready = true; break
                }
                // 若仍未就绪，额外触发一次图片页预热后再试（首两次）
                if (!ready && idx < 2 && !comicId.isNullOrEmpty()) {
                    runCatchingCancellable {
                        webClient.httpGet(
                            "https://$domain/comic/$comicId/chapter/$chapterId/images/all",
                            getRequestHeaders(),
                        ).close()
                    }
                }
            }
            // HEAD 在个别服务端可能不完全生效，最后补一次轻量 GET 以确认
            if (!ready) {
                ready = runCatchingCancellable {
                    webClient.httpGet(firstUrl, getRequestHeaders()).close()
                    true
                }.getOrDefault(false)
            }
        }
        return pages
    }

    private suspend fun apiCall(
        query: String,
        operationName: String? = null,
        variables: JSONObject? = null,
    ): JSONObject {
        val body = JSONObject().apply {
            put("operationName", operationName ?: JSONObject.NULL)
            put("variables", variables ?: JSONObject())
            put("query", query)
        }
        // 预热根域，尝试获取站点必要 Cookie
        runCatchingCancellable {
            webClient.httpGet("https://$domain/", getRequestHeaders()).close()
        }
        val response = try {
            webClient.httpPost("https://${domain}/api/query".toHttpUrl(), body, getRequestHeaders())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ParseException(
                "Komiic 接口请求失败（可能被拦截或接口错误），请在浏览器打开主页以通过挑战页，然后回到应用重试",
                "https://${domain}/",
                e,
            )
        }
        val ct = response.mimeType
        return if (ct != null && ct.contains("json")) {
            val root = response.parseJson()
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val msg = errors.optJSONObject(0)?.optString("message") ?: "未知错误"
                val hint = if (msg.contains("token is expired", true)) "登录已过期，请重新登录" else "接口返回错误"
                throw ParseException("Komiic 接口错误：$msg（$hint）", "https://${domain}/")
            }
            val dataObj = root.optJSONObject("data")
            dataObj ?: throw ParseException(
                "Komiic 接口返回异常 JSON，可能被拦截或接口错误，请在浏览器打开主页后重试",
                "https://${domain}/",
            )
        } else {
            val raw = response.parseRaw().trimStart()
            if (raw.startsWith("<")) {
                throw ParseException(
                    "Komiic 接口返回 HTML，可能被 Cloudflare/反爬拦截，请在浏览器打开主页以通过挑战页，然后回到应用重试",
                    "https://${domain}/",
                )
            }
            val root = JSONObject(raw)
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val msg = errors.optJSONObject(0)?.optString("message") ?: "未知错误"
                val hint = if (msg.contains("token is expired", true)) "登录已过期，请重新登录" else "接口返回错误"
                throw ParseException("Komiic 接口错误：$msg（$hint）", "https://${domain}/")
            }
            root.optJSONObject("data") ?: throw ParseException(
                "Komiic 接口返回非预期数据，请在浏览器打开主页后重试",
                "https://${domain}/",
            )
        }
    }
}
