package com.abrar.voyahiran.core.bridge

import org.junit.Assert.*
import org.junit.Test

class NeshanThemeXmlTest {
    @Test fun changesOnlyMapModeAndIsIdempotent() {
        val xml="<map><int name='MapLightMode' value='0'/><string name='saved'>home &amp; work</string><boolean name='voice' value='true'/></map>"
        val result=NeshanThemeXml.android(xml)
        assertTrue(NeshanThemeXml.isAndroid(result))
        assertTrue(result.contains("home &amp; work"))
        assertTrue(result.contains("name=\"voice\""))
        assertEquals(result,NeshanThemeXml.android(result))
    }
    @Test fun initializesMissingPreference() {assertTrue(NeshanThemeXml.isAndroid(NeshanThemeXml.android("<map/>")))}
}
