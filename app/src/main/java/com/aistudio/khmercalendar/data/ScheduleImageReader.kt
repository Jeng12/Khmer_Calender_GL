package com.aistudio.khmercalendar.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Reads the words in a roster photo with ML Kit's bundled Latin text
 * recogniser. Everything runs on the device: the picture is not uploaded and
 * no API key or network connection is needed.
 */
object ScheduleImageReader {

    suspend fun readTokens(context: Context, uri: Uri): Result<List<ScheduleImageParser.Token>> =
        withContext(Dispatchers.Default) {
            runCatching {
                // fromFilePath also applies the photo's EXIF rotation.
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val text = suspendCancellableCoroutine { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { cont.resume(Result.success(it)) }
                            .addOnFailureListener { cont.resume(Result.failure(it)) }
                    }.getOrThrow()
                    text.textBlocks.flatMap { block ->
                        block.lines.flatMap { line ->
                            line.elements.mapNotNull { element ->
                                val box = element.boundingBox ?: return@mapNotNull null
                                ScheduleImageParser.Token(
                                    element.text,
                                    box.left.toFloat(), box.top.toFloat(),
                                    box.right.toFloat(), box.bottom.toFloat()
                                )
                            }
                        }
                    }
                } finally {
                    recognizer.close()
                }
            }
        }
}
