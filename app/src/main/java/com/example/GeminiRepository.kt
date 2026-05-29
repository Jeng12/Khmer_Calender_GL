package com.example

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiRepository {

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = "gemini-2.0-flash",
                generationConfig = generationConfig {
                    temperature = 0.7f
                    maxOutputTokens = 300
                }
            )
    }

    /**
     * Returns a culturally rich explanation (Khmer + English) for why the given
     * KhmerDate is auspicious for its category. Wrapped in Result so callers
     * can handle failures gracefully (e.g. missing API key or no network).
     */
    suspend fun explainAuspiciousDay(khmerDate: KhmerDate): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildPrompt(khmerDate)
                val response = model.generateContent(prompt)
                response.text?.trim() ?: "No explanation available"
            }
        }

    private fun buildPrompt(khmerDate: KhmerDate): String {
        val category = khmerDate.auspiciousType ?: "ថ្ងៃល្អ"
        return """
            You are a Khmer cultural calendar expert. Answer in both Khmer and English (2-3 sentences each).

            Today is ${khmerDate.lunarDayName} of ${khmerDate.lunarMonthName},
            Buddhist Era ${khmerDate.BE}, year of the ${khmerDate.zodiac}.
            The moon phase is ${khmerDate.moonEmoji}.

            Briefly explain why this day is auspicious for: $category
            Reference traditional Khmer beliefs about lunar days, zodiac years, and ceremony timing.
        """.trimIndent()
    }
}
