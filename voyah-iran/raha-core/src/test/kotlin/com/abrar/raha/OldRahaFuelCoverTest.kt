package com.abrar.raha

import com.abrar.voicecommands.Recognition
import org.junit.Assert.*
import org.junit.Test

class OldRahaFuelCoverTest {
    @Test fun coversAreFuelScopedAndWorkWithoutLessons() {
        val parser = OldRahaRecognizer { "".byteInputStream() }
        for (cover in listOf("در", "درب", "درپوش", "محفظه")) {
            val text = "لطفا $cover باک بنزین رو باز کن برام"
            val r = parser.recognize(text)
            assertTrue("$text => $r", r is Recognition.Matched)
            assertEquals("OPEN_FUEL_FLAP", (r as Recognition.Matched).command.action)
        }
        val charge = parser.recognize("درپوش شارژر رو باز کن") as Recognition.Matched
        assertEquals("OPEN_CHARGE_PORT", charge.command.action)
        val door = parser.recognize("درب ماشین رو باز کن") as Recognition.Matched
        assertEquals("SET_DOOR_LOCK", door.command.action)
        for (text in listOf("درپوش باک بنزین رو ببند", "محفظه رو باز کن", "درپوش باک بنزین رو باز و بسته کن", "درپوش باک برق رو باز کن")) {
            val r = parser.recognize(text)
            assertTrue("$text => $r", r !is Recognition.Matched || r.command.action != "OPEN_FUEL_FLAP")
        }
    }
}
