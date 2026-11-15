package pt.gabriel.browser

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

enum class VtError { BAD_KEY, RATE_LIMIT, FAILED }

class VtException(val kind: VtError, cause: Throwable? = null) : Exception(kind.name, cause)

data class VtResult(
    val malicious: Int,
    val suspicious: Int,
    val harmless: Int,
    val undetected: Int,
    val reportUrl: String
) {
    val flagged get() = malicious + suspicious
    val total get() = malicious + suspicious + harmless + undetected
}

/**
 * Cliente mínimo da API v3 do VirusTotal (chave gratuita do utilizador).
 * Primeiro consulta um relatório existente; se não houver, submete o endereço e espera pelo resultado.
 * Bloqueante: chamar fora do thread principal.
 */
object VirusTotal {
    private const val API = "https://www.virustotal.com/api/v3"

    private fun reportUrl(url: String): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "https://www.virustotal.com/gui/url/$sha"
    }

    private fun urlId(url: String): String =
        Base64.encodeToString(url.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun call(key: String, method: String, path: String, form: String? = null): Pair<Int, String> {
        val c = URL("$API$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("x-apikey", key)
            c.setRequestProperty("accept", "application/json")
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("content-type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            c.disconnect()
        }
    }

    private fun check(code: Int) {
        when (code) {
            401, 403 -> throw VtException(VtError.BAD_KEY)
            429 -> throw VtException(VtError.RATE_LIMIT)
        }
    }

    private fun stats(o: JSONObject, link: String) = VtResult(
        o.optInt("malicious"), o.optInt("suspicious"), o.optInt("harmless"), o.optInt("undetected"), link
    )

    fun scan(key: String, url: String): VtResult {
        try {
            val link = reportUrl(url)

            // 1) relatório existente
            val (code, body) = call(key, "GET", "/urls/${urlId(url)}")
            check(code)
            if (code == 200) {
                val s = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
                    .getJSONObject("last_analysis_stats")
                return stats(s, link)
            }

            // 2) submeter e esperar (até ~25 s)
            val (pc, pb) = call(key, "POST", "/urls", "url=" + URLEncoder.encode(url, "UTF-8"))
            check(pc)
            if (pc !in 200..299) throw VtException(VtError.FAILED)
            val id = JSONObject(pb).getJSONObject("data").getString("id")
            repeat(8) {
                Thread.sleep(3_000)
                val (ac, ab) = call(key, "GET", "/analyses/$id")
                check(ac)
                if (ac == 200) {
                    val attr = JSONObject(ab).getJSONObject("data").getJSONObject("attributes")
                    if (attr.optString("status") == "completed") return stats(attr.getJSONObject("stats"), link)
                }
            }
            throw VtException(VtError.FAILED)
        } catch (e: VtException) {
            throw e
        } catch (e: Exception) {
            throw VtException(VtError.FAILED, e)
        }
    }
}
