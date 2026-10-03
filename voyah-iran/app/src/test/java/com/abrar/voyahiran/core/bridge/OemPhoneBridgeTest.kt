package com.abrar.voyahiran.core.bridge

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],manifest=Config.NONE,application=android.app.Application::class)
class OemPhoneBridgeTest {
    private val phone="AA:BB:CC:DD:EE:FF"
    private fun database()=SQLiteDatabase.create(null).apply {
        // Exact schema from BluetoothPhone ContactsProvider.DBOpenHelper; deliberately no _id.
        execSQL("create table contactsinfo(name text not null,mac text not null, number text not null);")
    }
    @Test fun readsEveryPageWithoutIdColumnAndExcludesOtherPhones() {
        database().use { db ->
            repeat(29) { index ->
                db.execSQL("INSERT INTO contactsinfo VALUES(?,?,?)",arrayOf("مخاطب $index",phone.lowercase(),"09120000%03d".format(index)))
                db.execSQL("INSERT INTO contactsinfo VALUES(?,?,?)",arrayOf("گوشی دیگر","11:22:33:44:55:66","09129999999"))
            }
            var after=0L
            val actual=mutableListOf<String>()
            var finished=false
            repeat(4) {
                val page=OemPhoneBridge.readPage(db,phone,after)
                val contacts=page.getJSONArray("contacts")
                assertTrue(contacts.length()<=12)
                for(i in 0 until contacts.length()) actual+=contacts.getJSONObject(i).getString("name")
                val next=page.getLong("after")
                finished=page.getBoolean("done")
                assertTrue(if(finished) next==after else next>after)
                after=next
            }
            assertTrue(finished)
            assertEquals((0 until 29).map { "مخاطب $it" },actual)
        }
    }
    @Test fun filteredRowsStillAdvanceCursorAndPhoneNumbersKeepInsertionOrder() {
        database().use { db ->
            repeat(12) { db.execSQL("INSERT INTO contactsinfo VALUES(?,?,?)",arrayOf("بدون شماره",phone,"")) }
            db.execSQL("INSERT INTO contactsinfo VALUES(?,?,?)",arrayOf("علی",phone,"+98 (912) 123-4567"))
            db.execSQL("INSERT INTO contactsinfo VALUES(?,?,?)",arrayOf("علی",phone,"02112345678"))
            val first=OemPhoneBridge.readPage(db,phone,0)
            assertEquals(0,first.getJSONArray("contacts").length())
            assertFalse(first.getBoolean("done"))
            val second=OemPhoneBridge.readPage(db,phone,first.getLong("after"))
            val contacts=second.getJSONArray("contacts")
            assertEquals("+989121234567",contacts.getJSONObject(0).getString("number"))
            assertEquals("02112345678",contacts.getJSONObject(1).getString("number"))
            assertTrue(OemPhoneBridge.readPage(db,phone,second.getLong("after")).getBoolean("done"))
        }
    }
}
