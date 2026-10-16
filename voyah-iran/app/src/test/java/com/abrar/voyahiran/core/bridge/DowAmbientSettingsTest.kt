package com.abrar.voyahiran.core.bridge

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class DowAmbientSettingsTest {
    open class Data {
        var value: String? = "1"
        var writes = 0
        var ignoreWrite = false
        val cursors = mutableListOf<Cursor>()
        fun rows(pkg: String, uri: Uri, projection: Array<String>, extras: Bundle): Cursor {
            assertEquals("root", pkg)
            assertEquals("content://qinggan.settings/global", uri.toString())
            assertArrayEquals(arrayOf("value"), projection)
            assertEquals("name='vehicle_setting_drive_mode_atm'", extras.getString("android:query-arg-sql-selection"))
            return MatrixCursor(arrayOf("value")).also { c ->
                value?.let { c.addRow(arrayOf(it)) }; cursors.add(c)
            }
        }
        fun save(values: ContentValues): Uri {
            assertEquals("vehicle_setting_drive_mode_atm", values.getAsString("name"))
            writes++
            if (!ignoreWrite) value = values.getAsString("value")
            return Uri.parse("content://qinggan.settings/global")
        }
    }
    class Pie : Data() {
        fun query(pkg: String, uri: Uri, projection: Array<String>, extras: Bundle, cancel: Any?): Cursor = rows(pkg, uri, projection, extras)
        fun insert(pkg: String, uri: Uri, values: ContentValues): Uri = save(values)
    }
    class R : Data() {
        fun query(pkg: String, tag: String?, uri: Uri, projection: Array<String>, extras: Bundle, cancel: Any?): Cursor {
            assertNull(tag); return rows(pkg, uri, projection, extras)
        }
        fun insert(pkg: String, tag: String?, uri: Uri, values: ContentValues, extras: Bundle): Uri {
            assertNull(tag); return save(values)
        }
    }
    @Test fun bothProtocolsReadWriteConfirmAndCloseCursors() {
        for (p in listOf(Pie(), R())) {
            assertEquals(1, DowAmbientSettings.readFrom(p, p.javaClass))
            DowAmbientSettings.setOn(p, p.javaClass, 0)
            assertEquals("0", p.value); assertEquals(1, p.writes)
            DowAmbientSettings.setOn(p, p.javaClass, 0)
            assertEquals(1, p.writes)
            assertTrue(p.cursors.all { it.isClosed })
        }
    }
    @Test fun absentRowUsesFactoryDefault() {
        val p = R().apply { value = null }
        assertEquals(0, DowAmbientSettings.readFrom(p, p.javaClass))
    }
    @Test fun malformedValueFailsAndClosesCursor() {
        val p = R().apply { value = "invalid" }
        assertThrows(IllegalStateException::class.java) { DowAmbientSettings.readFrom(p, p.javaClass) }
        assertTrue(p.cursors.all { it.isClosed })
    }
    @Test fun ignoredWriteFailsConfirmation() {
        val p = Pie().apply { ignoreWrite = true }
        assertThrows(IllegalStateException::class.java) { DowAmbientSettings.setOn(p, p.javaClass, 0) }
    }
}
