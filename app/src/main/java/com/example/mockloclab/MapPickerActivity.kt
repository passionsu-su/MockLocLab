package com.example.mockloclab

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 地图选点：高德瓦片 + Leaflet（离线打包），点图或搜索地名得到 GCJ-02 坐标后回传。
 * 地名搜索：填了高德 Web 服务 Key 用高德 POI 搜索（国内最准）；否则用 OpenStreetMap Nominatim（免 Key）。
 */
class MapPickerActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("places", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/map.html")
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun initial(): String {
            val lat = intent.getDoubleExtra("lat", 31.2304)
            val lon = intent.getDoubleExtra("lon", 121.4737)
            return JSONObject().put("lat", lat).put("lon", lon).put("zoom", 15)
                .put("name", intent.getStringExtra("name") ?: "").toString()
        }

        @JavascriptInterface
        fun search(query: String, centerLat: Double, centerLon: Double) {
            Thread {
                val json = try {
                    val key = prefs.getString("amap_key", "")?.trim().orEmpty()
                    if (key.isNotEmpty()) amapSearch(query, key) else nominatimSearch(query, centerLat, centerLon)
                } catch (e: Exception) {
                    JSONObject().put("error", "搜索失败：${e.message ?: e.javaClass.simpleName}（检查网络或代理）").toString()
                }
                runOnUiThread {
                    web.evaluateJavascript("window.onSearch(${JSONObject.quote(json)})", null)
                }
            }.start()
        }

        @JavascriptInterface
        fun confirm(lat: Double, lon: Double, name: String, start: Boolean) {
            runOnUiThread {
                setResult(RESULT_OK, Intent()
                    .putExtra("lat", lat).putExtra("lon", lon)
                    .putExtra("name", name).putExtra("start", start))
                finish()
            }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "MockLocLab/1.2 (Android; github.com/passionsu-su/MockLocLab)")
        c.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5")
        try {
            if (c.responseCode !in 200..299) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** 高德 POI 关键字搜索，返回 GCJ-02。 */
    private fun amapSearch(q: String, key: String): String {
        val body = get("https://restapi.amap.com/v3/place/text?keywords=${URLEncoder.encode(q, "UTF-8")}&offset=15&page=1&extensions=base&key=${URLEncoder.encode(key, "UTF-8")}")
        val o = JSONObject(body)
        if (o.optString("status") != "1") return JSONObject().put("error", "高德搜索失败：${o.optString("info")}").toString()
        val out = JSONArray()
        val pois = o.optJSONArray("pois") ?: JSONArray()
        for (i in 0 until pois.length()) {
            val p = pois.getJSONObject(i)
            val loc = p.optString("location").split(",")
            if (loc.size != 2) continue
            val lon = loc[0].toDoubleOrNull() ?: continue
            val lat = loc[1].toDoubleOrNull() ?: continue
            val addr = listOf(p.optString("cityname"), p.optString("adname"), p.optString("address"))
                .filter { it.isNotBlank() && it != "[]" }.joinToString(" ")
            out.put(JSONObject().put("name", p.optString("name")).put("addr", addr).put("lat", lat).put("lon", lon))
        }
        return out.toString()
    }

    /** OpenStreetMap Nominatim，返回 WGS-84，转成 GCJ-02 以对齐高德底图。 */
    private fun nominatimSearch(q: String, cLat: Double, cLon: Double): String {
        val c = CoordUtil.gcjToWgs(cLat, cLon)
        val box = "${c.second - 1.5},${c.first + 1.5},${c.second + 1.5},${c.first - 1.5}"
        val body = get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=15&q=${URLEncoder.encode(q, "UTF-8")}&viewbox=$box")
        val arr = JSONArray(body)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val lat = p.optString("lat").toDoubleOrNull() ?: continue
            val lon = p.optString("lon").toDoubleOrNull() ?: continue
            val g = CoordUtil.wgsToGcj(lat, lon)
            val full = p.optString("display_name")
            val name = p.optString("name").ifBlank { full.substringBefore(",") }
            out.put(JSONObject().put("name", name).put("addr", full).put("lat", g.first).put("lon", g.second))
        }
        return out.toString()
    }
}
