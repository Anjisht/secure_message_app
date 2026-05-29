package roy.ij.obscure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnalyticsPolicyTest {
    @Test
    fun firebaseAnalyticsAndCrashlyticsAreConfigured() {
        val rootBuild = File("../build.gradle.kts").readText()
        val appBuild = File("build.gradle.kts").readText()
        val versions = File("../gradle/libs.versions.toml").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(rootBuild.contains("libs.plugins.firebase.crashlytics"))
        assertTrue(appBuild.contains("libs.plugins.firebase.crashlytics"))
        assertTrue(versions.contains("firebase-analytics"))
        assertTrue(versions.contains("firebase-crashlytics"))
        assertTrue(manifest.contains("firebase_analytics_collection_enabled"))
        assertTrue(manifest.contains("firebase_crashlytics_collection_enabled"))
    }

    @Test
    fun analyticsImplementationAvoidsSensitiveIdentifiers() {
        val analyticsRoot = File("src/main/java/roy/ij/obscure/analytics")
        val analyticsFiles = analyticsRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("AnalyticsTracker") }
            .toList()

        assertTrue("Expected analytics implementation files", analyticsFiles.isNotEmpty())

        val combined = analyticsFiles.joinToString("\n") { it.readText() }
        assertFalse(combined.contains("setUserId"))
        assertFalse(combined.contains("username\""))
        assertFalse(combined.contains("roomId\""))
        assertFalse(combined.contains("message\""))
        assertFalse(combined.contains("token\""))
        assertFalse(combined.contains("qrContent\""))
    }
}
