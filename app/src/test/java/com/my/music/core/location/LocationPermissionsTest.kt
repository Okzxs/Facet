package com.my.music.core.location

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationPermissionsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `permission is denied by default`() {
        assertFalse(LocationPermissions.hasPermission(application))
    }

    @Test
    fun `coarse location is enough for memories`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(LocationPermissions.hasPermission(application))

        shadowOf(application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        assertFalse(LocationPermissions.hasPermission(application))
    }

    @Test
    fun `settings intent opens app details screen`() {
        LocationPermissions.openAppSettings(application)

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${application.packageName}", intent.data.toString())
    }
}
