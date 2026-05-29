package roy.ij.obscure

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenshotPolicyTest {
    @Test
    fun secureActivitiesBlockScreenshotsWithFlagSecure() {
        val sourceRoot = File("src/main/java")
        val requiredSecureActivities = listOf(
            "roy/ij/obscure/MainActivity.kt",
            "roy/ij/obscure/features/dm/PortraitCaptureActivity.kt"
        )

        requiredSecureActivities.forEach { relativePath ->
            val sourceFile = File(sourceRoot, relativePath)

            assertTrue("Expected source file to exist: $relativePath", sourceFile.exists())
            assertTrue(
                "Expected FLAG_SECURE in $relativePath",
                sourceFile.readText().contains("FLAG_SECURE")
            )
        }
    }
}
