package com.aistudio.khmercalendar.data

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthStore {
    private const val AUTH_FILE = "khmer_calendar_auth"
    private const val SESSION_KEY = "current_session"
    private const val LAST_USER_KEY = "last_active_user_id"
    private const val AUTH_BASE_URL = "https://api-calender-sigma.vercel.app/api/v1"
    private const val TIMEOUT_MS = 20_000

    data class Session(
        val userId: String,
        val displayName: String,
        val email: String,
        val accessToken: String,
        val createdAt: String? = null
    )

    sealed class AuthResult {
        data class Success(val session: Session) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

    fun currentSession(c: Context): Session? =
        prefs(c).getString(SESSION_KEY, null)?.let(::parseSession)

    fun isSignedIn(c: Context): Boolean = currentSession(c) != null

    suspend fun register(
        c: Context,
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return@withContext AuthResult.Error("Email is required")
        if (password.length < 6) return@withContext AuthResult.Error("Password must be at least 6 characters")
        val displayName = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { cleanEmail.substringBefore("@") }

        runCatching {
            val body = postJson(
                "/auth/register",
                JSONObject()
                    .put("name", displayName)
                    .put("email", cleanEmail)
                    .put("password", password)
            )
            activateSession(c, parseAuthSession(body))
        }.getOrElse { AuthResult.Error(authErrorMessage(it, "Registration failed")) }
    }

    suspend fun signIn(c: Context, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return@withContext AuthResult.Error("Email is required")
        if (password.isBlank()) return@withContext AuthResult.Error("Password is required")

        runCatching {
            val body = postJson(
                "/auth/login",
                JSONObject()
                    .put("email", cleanEmail)
                    .put("password", password)
            )
            activateSession(c, parseAuthSession(body))
        }.getOrElse { AuthResult.Error(authErrorMessage(it, "Incorrect email or password")) }
    }

    fun signOut(c: Context) {
        prefs(c).edit().remove(SESSION_KEY).apply()
        setInMemorySession(null)
    }

    fun clearForTests(c: Context) {
        prefs(c).edit().clear().apply()
        setInMemorySession(null)
    }

    fun authHeaders(): Map<String, String> {
        val session = inMemorySession ?: return emptyMap()
        return mapOf(
            "Authorization" to "Bearer ${session.accessToken}",
            "X-Calendar-User-Id" to session.userId,
            "X-Calendar-User-Email" to session.email
        )
    }

    fun setInMemorySession(session: Session?) {
        inMemorySession = session
    }

    suspend fun fetchProfile(c: Context, accessToken: String): Session? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$AUTH_BASE_URL/auth/me").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("User-Agent", "KhmerCalendarAndroid/1.0")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            val root = JSONObject(body)
            val dataObj = root.optJSONObject("data") ?: root
            val user = dataObj.optJSONObject("user") ?: dataObj
            val userId = user.opt("id")?.toString()?.trim().orEmpty()
            val email = user.optString("email").trim()
            val name = user.optString("name").trim().ifBlank { email.substringBefore("@") }
            val createdAt = user.optString("createdAt").ifBlank { user.optString("created_at") }.trim()
            Session(
                userId = userId,
                displayName = name,
                email = email,
                accessToken = accessToken,
                createdAt = createdAt.takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    private var inMemorySession: Session? = null

    internal fun saveSessionForTests(c: Context, session: Session): AuthResult =
        activateSession(c, session)

    private fun activateSession(c: Context, session: Session): AuthResult {
        val userId = session.userId
        val p = prefs(c)
        val previousUserId = p.getString(LAST_USER_KEY, null)
        if (previousUserId != null && previousUserId != userId) {
            AppStore.clearLocalUserData(c)
        }
        p.edit()
            .putString(LAST_USER_KEY, userId)
            .putString(SESSION_KEY, sessionJson(session).toString())
            .apply()
        setInMemorySession(session)
        return AuthResult.Success(session)
    }

    private fun parseSession(raw: String): Session? = runCatching {
        val o = JSONObject(raw)
        if (!o.optBoolean("api_authenticated", false)) return@runCatching null
        Session(
            userId = o.optString("user_id"),
            displayName = o.optString("display_name"),
            email = o.optString("email"),
            accessToken = o.optString("access_token"),
            createdAt = o.optString("created_at").takeIf { it.isNotBlank() }
        ).takeIf { it.userId.isNotBlank() && it.accessToken.isNotBlank() }
    }.getOrNull()

    private fun sessionJson(session: Session): JSONObject = JSONObject()
        .put("user_id", session.userId)
        .put("display_name", session.displayName)
        .put("email", session.email)
        .put("access_token", session.accessToken)
        .put("created_at", session.createdAt)
        .put("api_authenticated", true)

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun postJson(path: String, payload: JSONObject): String {
        val conn = (URL(AUTH_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "KhmerCalendarAndroid/1.0")
        }

        try {
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: ${body.take(200)}")
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{")) throw IOException("Authentication API returned an invalid response")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun parseAuthSession(body: String): Session {
        val data = JSONObject(body).getJSONObject("data")
        val user = data.getJSONObject("user")
        val token = data.optString("token").trim()
        val userId = user.opt("id")?.toString()?.trim().orEmpty()
        val email = user.optString("email").trim()
        val name = user.optString("name").trim().ifBlank { email.substringBefore("@") }
        val createdAt = user.optString("createdAt").ifBlank { user.optString("created_at") }.trim()
        return Session(
            userId = userId,
            displayName = name,
            email = email,
            accessToken = token,
            createdAt = createdAt.takeIf { it.isNotBlank() }
        )
    }

    private fun authErrorMessage(error: Throwable, fallback: String): String {
        val message = error.message.orEmpty()
        return when {
            "HTTP 401" in message || "HTTP 403" in message -> "Incorrect email or password"
            "HTTP 409" in message -> "An account already exists for this email"
            "HTTP 422" in message -> "Please check your account details"
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("failed to connect", ignoreCase = true) -> "Cannot reach Calendar API. Check your connection."
            else -> fallback
        }
    }

}
