package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class MovieboxOnlineProvider : MainAPI() {
    override var mainUrl = "https://movieboxonline.org"
    override var name = "MovieboxOnline"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon
    )

    override var lang = "id"
    override val hasMainPage = true

    // 1. Fitur Pencarian (Search)
    override async fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/id/searchpage?keyword=$query"
        val document = Jsoup.connect(searchUrl)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        val searchResults = mutableListOf<SearchResponse>()

        // Mengambil elemen kartu hasil pencarian
        document.select(".movielist-item, .search-item, .film-item").forEach { element ->
            val title = element.select(".title, .film-name, h3").text().trim()
            val href = element.select("a").attr("href")
            val posterUrl = element.select("img").attr("src")

            if (title.isNotEmpty() && href.isNotEmpty()) {
                val fullUrl = if (href.startsWith("http")) href else "$mainUrl$href"
                searchResults.add(
                    newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return searchResults
    }

    // 2. Mengambil Detail Film/Series & Episode
    override async fun load(url: String): LoadResponse {
        val document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        val title = document.select("h1, .detail-title").text().trim()
        val poster = document.select(".poster img, .detail-poster img").attr("src")
        val description = document.select(".synopsis, .description, .overview").text().trim()

        val episodes = mutableListOf<Episode>()

        // Mengambil daftar episode jika berupa TV Series
        document.select(".episode-item, .eps-list a").forEachIndexed { index, element ->
            val epHref = element.attr("href")
            val epName = element.text().ifEmpty { "Episode ${index + 1}" }
            val epUrl = if (epHref.startsWith("http")) epHref else "$mainUrl$epHref"

            episodes.add(
                Episode(
                    data = epUrl,
                    name = epName,
                    episode = index + 1
                )
            )
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    // 3. Ekstraksi Link Video (Extractor)
    override async fun loadLinks(
        data: String,
        isCdn: Boolean,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = Jsoup.connect(data)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .get()

        // Mencari iframe pemutar video
        val iframeUrl = document.select("iframe").attr("src")

        if (iframeUrl.isNotEmpty()) {
            val fullIframeUrl = if (iframeUrl.startsWith("http")) iframeUrl else "https:$iframeUrl"
            
            // Memanggil auto-extractor bawaan CloudStream untuk memproses player umum
            loadExtractor(fullIframeUrl, data, subtitleCallback, callback)
            return true
        }

        return false
    }
}
