package com.abrar.voyahiran.core.bridge

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class MicrogAutoSetupTest {
    @Test fun observesOnlyOfficialPackageInstallAndUpgradeEvents() {
        assertTrue(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_ADDED, "com.google.android.gms"))
        assertTrue(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_REPLACED, "com.google.android.gms"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_REMOVED, "com.google.android.gms"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_ADDED, "app.revanced.android.gms"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_ADDED, "com.android.vending"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_ADDED, "com.google.android.youtube"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_REPLACED, "com.google.android.youtube"))
        assertFalse(MicrogAutoSetup.relevant(Intent.ACTION_PACKAGE_ADDED, "com.example.app"))
        assertFalse(MicrogAutoSetup.relevant(null, null))
    }
    @Test fun runtimeAssetsMatchPinnedInstallerAssets() {
        val repo = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "voyah-iran-installer/tools/patch/microg").isDirectory }
        MicrogAutoSetup.assets.forEach { (name, expected) ->
            val bytes = File(repo, "voyah-iran-installer/tools/patch/microg/$name").readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals("Rebuild or update the pin deliberately: $name", expected, actual)
        }
    }
}
