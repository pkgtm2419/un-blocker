package com.unblocker.app.manifest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestServiceDeclarationTest {

    @Test
    fun testOnlyCanonicalUnblockerVpnServiceIsDeclared() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestText = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }

        val canonicalService = "com.unblocker.app.services.UnblockerVpnService"
        val deprecatedService = "com.unblocker.vpn.service.UnblockerVpnService"

        assertTrue(
            "Canonical VPN service $canonicalService must be declared in AndroidManifest.xml",
            manifestText.contains(canonicalService)
        )

        assertFalse(
            "Deprecated VPN service $deprecatedService must NOT be declared in AndroidManifest.xml",
            manifestText.contains(deprecatedService)
        )
    }

    @Test
    fun testVpnServicePermissionAndIntentFilterDeclared() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestText = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }

        assertTrue(
            "Service must require BIND_VPN_SERVICE permission",
            manifestText.contains("android:permission=\"android.permission.BIND_VPN_SERVICE\"")
        )
        assertTrue(
            "Service must have android.net.VpnService intent filter action",
            manifestText.contains("<action android:name=\"android.net.VpnService\" />")
        )
    }
}
