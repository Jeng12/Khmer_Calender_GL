package com.example.data

import com.example.calendar.KhmerDate
import com.example.core.AppLanguage
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
    suspend fun explainAuspiciousDay(
        khmerDate: KhmerDate,
        language: AppLanguage = AppLanguage.KM
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildPrompt(khmerDate, language)
                val response = model.generateContent(prompt)
                response.text?.trim() ?: "No explanation available"
            }
        }

    private fun buildPrompt(khmerDate: KhmerDate, language: AppLanguage): String {
        val category = khmerDate.auspiciousType ?: "ថ្ងៃល្អ"
        val answerInstruction = if (language == AppLanguage.EN) {
            "Answer in English only (2-3 sentences)."
        } else {
            "Answer in Khmer only (2-3 sentences)."
        }
        return """
            You are a Khmer cultural calendar expert. $answerInstruction

            Today is ${khmerDate.lunarDayName} of ${khmerDate.lunarMonthName},
            Buddhist Era ${khmerDate.BE}, year of the ${khmerDate.zodiac}.
            The moon phase is ${khmerDate.moonEmoji}.

            Briefly explain why this day is auspicious for: $category
            Reference traditional Khmer beliefs about lunar days, zodiac years, and ceremony timing.
        """.trimIndent()
    }
}
