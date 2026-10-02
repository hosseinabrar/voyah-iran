package com.abrar.voyahiran.feature.steering.domain

import org.junit.Assert.*
import org.junit.Test

class QingganKeyCodecTest {
    @Test fun legacyDoesNotConsumeCallbackTrailer() {
        val values = arrayListOf<Any?>(130, 8192, "bundle", "caller.package", 12)
        val event=QingganKeyCodec(false).read({ values.removeAt(0) as Int }, { values.removeAt(0) })
        assertEquals(130,event.code); assertEquals(8192,event.action)
        assertEquals(0,event.flags); assertEquals(0,event.display)
        assertEquals("bundle",event.payload)
        assertEquals(listOf("caller.package",12),values)
    }
    @Test fun modernKeepsFlagsAndDisplay() {
        val values = arrayListOf<Any?>(33, 8192, 7, null, 1, "trailer")
        val event=QingganKeyCodec(true).read({ values.removeAt(0) as Int }, { values.removeAt(0) })
        assertEquals(7,event.flags); assertEquals(1,event.display); assertNull(event.payload)
        assertEquals(listOf("trailer"),values)
    }
    @Test fun injectionUsesMatchingLayout() {
        for(extended in listOf(false,true)) {
            val written=mutableListOf<Any?>()
            QingganKeyCodec(extended).write(QingganKeyCodec.Event(33,8192,5,"payload",0),
                { written.add(it) }, { written.add(it) })
            assertEquals(if(extended) listOf(33,8192,5,"payload",0) else listOf(33,8192,"payload"),written)
        }
    }
}
