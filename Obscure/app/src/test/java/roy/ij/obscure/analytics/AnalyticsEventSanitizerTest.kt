package roy.ij.obscure.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsEventSanitizerTest {
    @Test
    fun consentDefaultsToDisabled() {
        assertFalse(AnalyticsConsent.defaultEnabled)
    }

    @Test
    fun sanitizerRejectsSensitiveKeys() {
        val clean = AnalyticsEventSanitizer.sanitize(
            mapOf(
                "feature" to "chat",
                "username" to "alice",
                "roomId" to "room-123",
                "message" to "hello",
                "token" to "secret",
                "qrContent" to "{\"userId\":\"1\"}"
            )
        )

        assertEquals(mapOf("feature" to "chat"), clean)
    }

    @Test
    fun sanitizerNormalizesEventNames() {
        assertEquals("send_message_tap", AnalyticsEventSanitizer.eventName("Send Message Tap!"))
    }
}
