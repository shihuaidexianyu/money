package com.shihuaidexianyu.money

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportJsonFileWriterTest {
    @Test
    fun writeCreatesShareableFileProviderUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = ExportJsonFileWriter(context).write(
            json = """{"metadata":{"schemaVersion":1}}""",
            timestamp = 1_700_000_000_000L,
        )

        assertEquals("application/json", result.mimeType)
        assertTrue(result.fileName.startsWith("money-export-"))
        assertTrue(result.fileName.endsWith(".json"))
        assertEquals("content", result.uri.scheme)
        assertEquals("${context.packageName}.fileprovider", result.uri.authority)

        val exportedText = context.contentResolver
            .openInputStream(result.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals("""{"metadata":{"schemaVersion":1}}""", exportedText)
    }
}
