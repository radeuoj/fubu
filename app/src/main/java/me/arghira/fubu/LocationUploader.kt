package me.arghira.fubu

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LocationUploader {
    private const val ENDPOINT = "https://example.com/locations"

    suspend fun upload(location: Location, completion: (() -> Unit)? = null) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("lat", location.latitude)
                    .put("lng", location.longitude)
                    .put("accuracy", location.accuracy.toDouble())
                    .put("timestamp", location.time)

                val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) "OK" else "HTTP $code"
            }.getOrElse { it.message ?: "error" }
        }
        Log.d("FubuLocation", "Upload: $result")
        completion?.invoke()
    }
}
