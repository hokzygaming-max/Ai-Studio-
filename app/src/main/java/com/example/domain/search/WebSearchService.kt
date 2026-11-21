package com.example.domain.search

import com.example.domain.model.SearchCitation
import com.example.domain.model.WebSearchConfig
import com.example.domain.model.WebSearchProviderType
import com.example.domain.provider.ProviderHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

data class WebSearchResult(
    val citations: List<SearchCitation>,
    val contextPromptBlock: String,
    val errorMessage: String? = null
)

class WebSearchService {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun search(
        query: String,
        config: WebSearchConfig,
        apiKey: String
    ): WebSearchResult = withContext(Dispatchers.IO) {
        if (config.providerType == WebSearchProviderType.NONE) {
            return@withContext WebSearchResult(
                citations = emptyList(),
                contextPromptBlock = "",
                errorMessage = "Live web search is unavailable because no search provider is configured in Settings."
            )
        }
        if (config.providerType.requiresKey && apiKey.isBlank()) {
            return@withContext WebSearchResult(
                citations = emptyList(),
                contextPromptBlock = "",
                errorMessage = "${config.providerType.displayName} API key is missing. Configure it in Settings -> Web Search."
            )
        }

        return@withContext runCatching {
            when (config.providerType) {
                WebSearchProviderType.TAVILY -> searchTavily(query, apiKey, config.customEndpoint)
                WebSearchProviderType.BRAVE -> searchBrave(query, apiKey, config.customEndpoint)
                WebSearchProviderType.SERPER -> searchSerper(query, apiKey, config.customEndpoint)
                WebSearchProviderType.SEARXNG -> searchSearxng(query, config.customEndpoint)
                WebSearchProviderType.NONE -> emptyList()
            }
        }.fold(
            onSuccess = { citations ->
                if (citations.isEmpty()) {
                    WebSearchResult(emptyList(), "", "Search returned 0 results for '$query'.")
                } else {
                    val block = buildString {
                        appendLine("Use these verified live web search results and cite them with [index] URLs:")
                        citations.forEach { c ->
                            appendLine("[${c.index}] ${c.title} (${c.url})")
                            appendLine("    ${c.snippet}")
                        }
                    }
                    WebSearchResult(citations, block, null)
                }
            },
            onFailure = { err ->
                WebSearchResult(emptyList(), "", "Web search failed: ${err.localizedMessage}")
            }
        )
    }

    private fun searchTavily(query: String, apiKey: String, customEndpoint: String): List<SearchCitation> {
        val url = customEndpoint.ifBlank { WebSearchProviderType.TAVILY.placeholderEndpoint }
        val payload = JSONObject().apply {
            put("api_key", apiKey.trim())
            put("query", query)
            put("max_results", 5)
            put("search_depth", "basic")
        }
        val req = Request.Builder().url(url).post(payload.toString().toRequestBody(jsonMedia)).build()
        ProviderHttpClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Tavily HTTP ${resp.code}: ${body.take(180)}")
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return List(results.length()) { i ->
                val o = results.getJSONObject(i)
                SearchCitation(
                    index = i + 1,
                    title = o.optString("title"),
                    url = o.optString("url"),
                    snippet = o.optString("content").take(320)
                )
            }
        }
    }

    private fun searchBrave(query: String, apiKey: String, customEndpoint: String): List<SearchCitation> {
        val base = customEndpoint.ifBlank { WebSearchProviderType.BRAVE.placeholderEndpoint }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("$base?q=$encoded&count=5")
            .get()
            .header("X-Subscription-Token", apiKey.trim())
            .header("Accept", "application/json")
            .build()
        ProviderHttpClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Brave Search HTTP ${resp.code}: ${body.take(180)}")
            val results = JSONObject(body).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
            return List(results.length()) { i ->
                val o = results.getJSONObject(i)
                SearchCitation(
                    index = i + 1,
                    title = o.optString("title"),
                    url = o.optString("url"),
                    snippet = o.optString("description").take(320)
                )
            }
        }
    }

    private fun searchSerper(query: String, apiKey: String, customEndpoint: String): List<SearchCitation> {
        val url = customEndpoint.ifBlank { WebSearchProviderType.SERPER.placeholderEndpoint }
        val payload = JSONObject().apply {
            put("q", query)
            put("num", 5)
        }
        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("X-API-KEY", apiKey.trim())
            .build()
        ProviderHttpClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Serper HTTP ${resp.code}: ${body.take(180)}")
            val results = JSONObject(body).optJSONArray("organic") ?: return emptyList()
            return List(results.length()) { i ->
                val o = results.getJSONObject(i)
                SearchCitation(
                    index = i + 1,
                    title = o.optString("title"),
                    url = o.optString("link"),
                    snippet = o.optString("snippet").take(320)
                )
            }
        }
    }

    private fun searchSearxng(query: String, customEndpoint: String): List<SearchCitation> {
        val base = customEndpoint.trim().trimEnd('/')
        if (base.isBlank()) error("Enter your SearXNG instance URL in Settings.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val target = if (base.endsWith("/search")) "$base?q=$encoded&format=json" else "$base/search?q=$encoded&format=json"
        val req = Request.Builder().url(target).get().build()
        ProviderHttpClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("SearXNG HTTP ${resp.code}: ${body.take(180)}")
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return List(minOf(5, results.length())) { i ->
                val o = results.getJSONObject(i)
                SearchCitation(
                    index = i + 1,
                    title = o.optString("title"),
                    url = o.optString("url"),
                    snippet = o.optString("content").take(320)
                )
            }
        }
    }
}
