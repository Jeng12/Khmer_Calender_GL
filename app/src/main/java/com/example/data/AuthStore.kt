package com.example.data

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object AuthStore {
    private const val AUTH_FILE = "khmer_calendar_auth"
    private const val SESSION_KEY = "current_session"
    private const val LAST_USER_KEY = "last_active_user_id"

    data class Session(
        val userId: String,
        val displayName: String,
        val email: String,
        val accessToken: String
    )

    sealed class AuthResult {
        data class Success(val session: Session) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    private fun prefs(c: Context) = c.getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

    fun currentSession(c: Context): Session? =
        prefs(c).getString(SESSION_KEY, null)?.let(::parseSession)

    fun isSignedIn(c: Context): Boolean = currentSession(c) != null

    fun register(
        c: Context,
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): AuthResult {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) return AuthResult.Error("Email is required")
        if (password.length < 6) return AuthResult.Error("Password must be at least 6 characters")
        val accountKey = accountKey(cleanEmail)
        if (prefs(c).contains(accountKey)) return AuthResult.Error("An account already exists for this email")

        val displayName = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { cleanEmail.substringBefore("@") }
        val userId = sha256("khmer-calendar-user:$cleanEmail").take(32)
        val salt = randomHex(16)
        val account = JSONObject()
            .put("user_id", userId)
            .put("display_name", displayName)
            .put("email", cleanEmail)
            .put("salt", salt)
            .put("password_hash", passwordHash(cleanEmail, password, salt))
        prefs(c).edit().putString(accountKey, account.toString()).apply()

        return activateSession(c, account)
    }

    fun signIn(c: Context, email: String, password: String): AuthResult {
        val cleanEmail = normalizeEmail(email)
        val account = prefs(c).getString(accountKey(cleanEmail), null)?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return AuthResult.Error("Account not found")
        val salt = account.optString("salt")
        val expected = account.optString("password_hash")
        if (expected.isBlank() || expected != passwordHash(cleanEmail, password, salt)) {
            return AuthResult.Error("Incorrect email or password")
        }
        return activateSession(c, account)
    }

    fun signOut(c: Context) {
        prefs(c).edit().remove(SESSION_KEY).apply()
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

    private var inMemorySession: Session? = null

    private fun activateSession(c: Context, account: JSONObject): AuthResult {
        val userId = account.optString("user_id")
        val session = Session(
            userId = userId,
            displayName = account.optString("display_name").ifBlank { account.optString("email") },
            email = account.optString("email"),
            accessToken = randomToken()
        )
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
        Session(
            userId = o.optString("user_id"),
            displayName = o.optString("display_name"),
            email = o.optString("email"),
            accessToken = o.optString("access_token")
        ).takeIf { it.userId.isNotBlank() && it.accessToken.isNotBlank() }
    }.getOrNull()

    private fun sessionJson(session: Session): JSONObject = JSONObject()
        .put("user_id", session.userId)
        .put("display_name", session.displayName)
        .put("email", session.email)
        .put("access_token", session.accessToken)

    private fun normalizeEmail(email: String): String = email.trim().lowercase()
    private fun accountKey(email: String): String = "account_${sha256(email)}"

    private fun passwordHash(email: String, password: String, salt: String): String =
        sha256("$salt:$email:$password")

    private fun randomToken(): String = "${UUID.randomUUID()}-${randomHex(16)}"

    private fun randomHex(bytes: Int): String {
        val data = ByteArray(bytes)
        SecureRandom().nextBytes(data)
        return data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
