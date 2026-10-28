package com.abrar.voyahiran.feature.launcher

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class StaticLauncherArtworkContractTest {
    private val sources=File("src/main/java/com/abrar/voyahiran/feature/launcher")
    @Test fun fixedCardEffectsNeverReturnToRuntime() {
        for(name in listOf("PassengerClimateView","PassengerSeatsView","PassengerWeatherView","PassengerFavoritesView","PassengerMusicView","LauncherVehicleDashboard")){
            val source=File(sources,"$name.kt").readText()
            for(effect in listOf("LinearGradient(","RadialGradient(","ColorMatrix(","PorterDuffColorFilter(","getPixels(","createScaledBitmap("))
                assertFalse("$name must use offline assets, not $effect",source.contains(effect))
        }
        val scent=File(sources,"PassengerFragranceView.kt").readText()
        assertFalse(scent.contains("RadialGradient("));assertFalse(scent.contains("private fun filter("))
        assertTrue("Steady off uses baked grayscale, not a shader",scent.contains("grayscale=!on&&power<=0f"))
        assertTrue("Saturation shader is allowed only during power transition",scent.contains("if(!grayscale&&power<1f)"))
    }
    @Test fun everyFixedThemeVariantIsBundled() {
        val assets=File("src/main/assets/passenger/baked")
        for(theme in listOf("light","dark")){
            for(card in listOf("climate","weather","music","seats","favorites","vehicle-card","clock","all-apps","music-play","central-play","show-map"))
                assertTrue("Missing $card-$theme",File(assets,"$card-$theme.png").length()>0)
            for(tone in 1..3)for(on in listOf(false,true))
                assertTrue(File(assets,"fragrance-$theme-$tone-$on.png").length()>0)
            for(tone in 1..3)assertTrue(File(assets,"fragrance-$theme-$tone-false-gray.png").length()>0)
        }
        val helper=File(sources,"StaticLauncherArtwork.kt").readText()
        assertFalse(helper.contains("BitmapFactory"));assertTrue(helper.contains("PassengerArtworkCache.get"))
    }
    @Test fun newlyBakedArtNeverReturnsToRuntime(){
        val weather=File(sources,"LauncherWeatherArtwork.kt").readText()
        for(effect in listOf("getPixels(","Canvas(","createBitmap("))assertFalse(weather.contains(effect))
        for(name in listOf("LauncherLoadingView","LauncherDashboard"))assertFalse(File(sources,"$name.kt").readText().contains("SweepGradient("))
        val orb=File("../raha-feedback/src/main/java/com/abrar/raha/feedback/RahaOrbView.kt").readText()
        assertFalse(orb.contains("RadialGradient("))
        assertFalse(orb.contains("for(i in 0 until 40)"))
        for(theme in listOf("light","dark")){
            assertTrue(File("src/main/assets/passenger/baked/loading-$theme.png").length()>0)
            for(state in listOf("normal","success","failure","unknown","processing"))
                assertTrue(File("../raha-feedback/src/main/assets/raha/baked/$state-$theme.png").length()>0)
        }
    }
}
