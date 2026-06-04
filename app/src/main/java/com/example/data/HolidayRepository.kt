package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * A single Cambodian public holiday as returned by the
 * Khmer Public Holidays API (https://khmer-public-holidays-api.vercel.app).
 *
 * The remote `type` field is one of: fixed, national, international,
 * religious, royal, traditional. [isBuddhist] folds the religious ones into
 * the app's two-bucket National / Buddhist filter.
 */
data class Holiday(
    val nameKh: String,
    val nameEn: String,
    val date: LocalDate,
    val type: String,
    val description: String?,
    val notes: String?,
    val isFixed: Boolean
) {
    /** Religious holidays (Visak Bochea, Meak Bochea, Pchum Ben…) map to the Buddhist bucket. */
    val isBuddhist: Boolean get() = type.equals("religious", ignoreCase = true)
}

/**
 * Fetches Cambodian public holidays from the public Khmer holidays API.
 *
 * Uses a plain [HttpURLConnection] + `org.json` so no extra networking
 * dependency is required. Results are cached in memory for the process
 * lifetime; callers get a [Result] so network/parse failures can fall back to
 * the bundled list gracefully.
 */
object HolidayRepository {

    private const val ENDPOINT = "https://khmer-public-holidays-api.vercel.app/holidays"
    private const val TIMEOUT_MS = 12_000

    @Volatile
    private var cache: List<Holiday>? = null

    /**
     * @param year  Optional year filter applied client-side after fetching.
     * @param forceRefresh  Bypass the in-memory cache and re-fetch from network.
     */
    suspend fun fetchHolidays(
        year: Int? = null,
        forceRefresh: Boolean = false
    ): Result<List<Holiday>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = cache.takeUnless { forceRefresh || it == null } ?: run {
                val parsed = parse(download())
                cache = parsed
                parsed
            }
            if (year == null) all else all.filter { it.date.year == year }
        }
    }

    private fun download(): String {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from holidays API")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Holiday> {
        val arr = JSONArray(body)
        val out = ArrayList<Holiday>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dateStr = o.optString("date").takeIf { it.isNotBlank() } ?: continue
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            out += Holiday(
                nameKh = o.optString("name_kh", ""),
                nameEn = o.optString("name_en", ""),
                date = date,
                type = o.optString("type", "national"),
                description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                isFixed = o.optBoolean("is_fixed", false)
            )
        }
        return out.sortedBy { it.date }
    }
}
