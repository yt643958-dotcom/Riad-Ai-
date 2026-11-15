package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RIAD AI", appName)
    }

    @Test
    fun `crypto utils base64 and sha256 work accurately`() {
        val encoded = CryptoUtils.encodeBase64("RIAD_AI")
        val decoded = CryptoUtils.decodeBase64(encoded).getOrThrow()
        assertEquals("RIAD_AI", decoded)

        val sha256 = CryptoUtils.hash("abc", "SHA-256")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256)
        assertTrue(CryptoUtils.analyzePasswordEntropy("Tr0ub4dor&3!xYz#99").bits > 80.0)
    }
}
