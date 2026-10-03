package com.abrar.raha

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FinglishContactNamesTest {
    @Test fun aaptUnpackedAssetsLoadDictionaryAndFallback() {
        val directory=File("../app/src/main/assets/contact-names")
        val converter=FinglishContactNames { name ->
            if(name.endsWith(".gz")) throw java.io.FileNotFoundException(name)
            if(name.endsWith(".tsv")) java.util.zip.GZIPInputStream(File(directory,"$name.gz").inputStream())
            else File(directory,name).inputStream()
        }
        assertEquals("علی",converter.convert("Ali"))
        assertTrue(converter.convert("zzzxqvvv").isNotBlank())
    }
    @Test fun bundledDictionaryAndOfflineFallback() {
        val directory=File("../app/src/main/assets/contact-names")
        val converter=FinglishContactNames { File(directory,it).inputStream() }
        assertEquals("علی",converter.convert("Ali"))
        assertEquals("فرهاد",converter.convert("farhad"))
        assertEquals("مهناز",converter.convert("mahnaz"))
        assertEquals("امید",converter.convert("omid"))
        assertEquals("پژمان",converter.convert("pejman"))
        assertEquals("هدیه",converter.convert("hedieh"))
        assertEquals("علی جعفری",converter.convert("علی جعفری"))
        assertTrue(converter.convert("zzzxqvvv aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").isNotBlank())
    }
}
