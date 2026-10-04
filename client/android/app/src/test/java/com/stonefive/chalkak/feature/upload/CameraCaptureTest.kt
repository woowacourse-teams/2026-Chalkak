package com.stonefive.chalkak.feature.upload

import android.content.pm.PackageManager
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureTest {
    @Test
    fun `Android 9 이하에서 저장 권한이 없으면 갤러리 권한을 요청한다`() {
        val shouldRequest = shouldRequestLegacyGalleryPermission(
            sdkInt = Build.VERSION_CODES.P,
            permissionStatus = PackageManager.PERMISSION_DENIED,
        )

        assertTrue(shouldRequest)
    }

    @Test
    fun `Android 10 이상에서는 저장 권한을 요청하지 않는다`() {
        val shouldRequest = shouldRequestLegacyGalleryPermission(
            sdkInt = Build.VERSION_CODES.Q,
            permissionStatus = PackageManager.PERMISSION_DENIED,
        )

        assertFalse(shouldRequest)
    }

    @Test
    fun `갤러리 저장 파일명은 찰칵 prefix와 jpg 확장자를 사용한다`() {
        val displayName = cameraCaptureGalleryDisplayName("2dd7e827-444d-4547-9b3f-eaa6cf1fe916.jpg")

        assertEquals("chalkak-photo-2dd7e827-444d-4547-9b3f-eaa6cf1fe916.jpg", displayName)
    }
}
