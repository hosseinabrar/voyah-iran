package com.abrar.voyahiran.feature.launcher

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PassengerNativeOnlyTest {
    @Test fun launcherHasNoHiddenBrowserOrImageEncodingBridge() {
        val source=File("src/main/java/com/abrar/voyahiran/feature/launcher/PassengerLauncherActivity.kt").readText()
        for(token in listOf("WebView","android.webkit","evaluateJavascript","loadUrl","JavascriptInterface","pictures[","ByteArrayOutputStream","nativeOverlay"))
            assertFalse("Obsolete browser path: $token",source.contains(token))
        for(card in listOf("climate","fragrance","favoriteCard","weatherCard","seatsCard","musicCard"))
            assertTrue("Native card missing: $card",source.contains("surface.addView($card,"))
        assertTrue(source.contains("val scale=surface.width/1920f"))
        assertTrue(source.contains("surface.setBackgroundColor(LauncherWindowTheme.background(light))"))
        assertTrue(source.contains("favoriteCard.bind(catalog,ids)"))
        assertTrue(source.contains("favoriteCard.dismissEdit()"))
    }
    @Test fun legacyFilesCannotAccidentallyReturnToThePackage() {
        val assets=File("src/main/assets")
        for(path in listOf("passenger/dark.html","passenger/light.html","passenger/runtime.js","passenger/runtime.css",
            "passenger/oem-x/seat_bg_high_level_97c.png","passenger/oem-x/seat_upholstery_free.png",
            "passenger/fragrance-wide/landscape.png","passenger/fragrance-wide/landscape-day-v1.png",
            "launcher/ccc/energy_car.png","launcher/ccc/energy_car_light.png"))
            assertFalse("Legacy asset still packaged: $path",File(assets,path).exists())
        for(theme in listOf("light","dark")){
            for(i in 0..19)assertFalse(File(assets,"launcher/ccc/body_${theme}_$i.png").exists())
            for(i in 20..80)assertTrue(File(assets,"launcher/ccc/body_${theme}_$i.png").isFile)
        }
    }
}
