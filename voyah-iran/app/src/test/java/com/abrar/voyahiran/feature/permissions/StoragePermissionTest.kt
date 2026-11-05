package com.abrar.voyahiran.feature.permissions

import org.junit.Assert.*
import org.junit.Test
import com.abrar.voyahiran.core.bridge.BridgePolicy

class StoragePermissionTest {
    @Test fun storagePermissionsMatchAndroidVersion() {
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"),PermissionRepository.storagePermissions(28))
        assertEquals(PermissionRepository.storagePermissions(28),PermissionRepository.storagePermissions(29))
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE"),PermissionRepository.storagePermissions(30))
        assertEquals(PermissionRepository.storagePermissions(30),PermissionRepository.storagePermissions(32))
        assertEquals(4,PermissionRepository.storagePermissions(33).size)
        assertFalse(PermissionRepository.storagePermissions(33).contains("android.permission.READ_EXTERNAL_STORAGE"))
    }
    @Test fun bridgeAllowsVerifiedStorageAppOpsForUserZeroOnly() {
        assertNotNull(BridgePolicy.resolve(listOf("cmd","appops","set","--user","0","com.example.app","MANAGE_EXTERNAL_STORAGE","allow")))
        assertNotNull(BridgePolicy.resolve(listOf("cmd","appops","get","--user","0","com.example.app","MANAGE_EXTERNAL_STORAGE")))
        assertNull(BridgePolicy.resolve(listOf("cmd","appops","set","--user","10","com.example.app","MANAGE_EXTERNAL_STORAGE","allow")))
    }
}
