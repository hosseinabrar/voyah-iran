package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class NeshanThemeXmlTest {
    @Test fun changesOnlyMapModeAndIsIdempotent() {
        val xml="<map><int name='MapLightMode' value='0'/><string name='saved'>home &amp; work</string><boolean name='voice' value='true'/></map>"
        val result=NeshanThemeXml.android(xml,"MapLightMode",3)
        assertTrue(NeshanThemeXml.isAndroid(result,"MapLightMode",3))
        assertTrue(result.contains("home &amp; work"))
        assertTrue(result.contains("name=\"voice\""))
        assertEquals(result,NeshanThemeXml.android(result,"MapLightMode",3))
    }
    @Test fun initializesMissingPreference() {assertTrue(NeshanThemeXml.isAndroid(NeshanThemeXml.android("<map/>","OtherKey",7),"OtherKey",7))}
}
