package roy.ij.obscure.util

object Jwt {
    fun subject(token: String): String? {
        return try {
            val parts = token.split(".")
            val body = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val json = org.json.JSONObject(String(body))
            json.optString("sub", null)
        } catch (e: Exception) { null }
    }
}