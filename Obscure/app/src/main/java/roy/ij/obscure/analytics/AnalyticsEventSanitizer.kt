package roy.ij.obscure.analytics

object AnalyticsEventSanitizer {
    private val sensitiveKeys = setOf(
        "alias",
        "contact",
        "message",
        "password",
        "qrcontent",
        "roomid",
        "targetuserid",
        "token",
        "userid",
        "username"
    )

    fun eventName(name: String): String {
        val normalized = name
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

        return normalized.take(40).ifBlank { "event" }
    }

    fun sanitize(params: Map<String, String>): Map<String, String> =
        params
            .filterKeys { key -> key.lowercase() !in sensitiveKeys }
            .mapKeys { (key, _) -> eventName(key) }
            .mapValues { (_, value) -> value.take(80) }
}
