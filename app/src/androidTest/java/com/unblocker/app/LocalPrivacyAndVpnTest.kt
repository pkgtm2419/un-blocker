package com.unblocker.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.VpnService
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.ContentFilterEngine
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.PrivateReputationStore
import com.unblocker.app.receivers.BootReceiver
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.services.UnblockerVpnService
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPrivacyAndVpnTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = FilteringPreferences.getInstance(context)

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun awaitState(expected: ServiceStatus) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (UnblockerVpnService.session.status.value != expected && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(expected, UnblockerVpnService.session.status.value)
    }

    private fun openApp() {
        // Exercise VPN consent as the app, not with the shell's substituted identity.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(500)
    }

    private fun awaitVpnRouting() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline) {
            val network = connectivity.activeNetwork
            if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                connectivity.getLinkProperties(network)?.dnsServers?.any { it.hostAddress == "10.10.0.1" } == true) return
            Thread.sleep(50)
        }
        fail("Android did not publish the VPN as the app's default DNS network")
    }

    @After fun cleanup() {
        UnblockerVpnService.stop(context)
        shell("appops set com.unblocker.app ACTIVATE_VPN deny")
        DeviceLearning.clear(context)
        DeviceLearning.clearAllowlist(context)
        DeviceLearning.clearBlocklist(context)
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test fun keystoreStoreContainsNoRawDomainAndResetPersists() {
        val store = DeviceLearning.store(context)
        store.put("ads.private-example.test", 0.9f)
        val file = File(context.noBackupFilesDir, "learning-v1")
        val key = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey("unblocker.learning.hmac.v1", null) as javax.crypto.SecretKey
        assertNull(key.encoded)
        assertTrue(file.exists())
        assertFalse(file.readText().contains("private-example"))
        assertEquals(0.9f, store.get("ads.private-example.test")!!, 0f)
        assertEquals(0.9f, PrivateReputationStore(key, file).get("ads.private-example.test")!!, 0f)
        DeviceLearning.clear(context)
        assertNull(store.get("ads.private-example.test"))
        assertEquals(0, PrivateReputationStore(key, file).size())
        assertFalse(File(context.filesDir, "learned_trackers.txt").exists())
    }

    @Test fun filterSwitchReevaluatesPreviouslyAllowedDomain() {
        preferences.setAdultBlockingEnabled(false)
        preferences.setAdBlockingEnabled(false)
        val engine = ContentFilterEngine(context, preferences = preferences)
        assertFalse(engine.analyzeAndFilter("pornhub.com").shouldBlock)
        preferences.setAdultBlockingEnabled(true)
        assertTrue(engine.analyzeAndFilter("pornhub.com").shouldBlock)
        preferences.setAdultBlockingEnabled(false)
        assertFalse(engine.analyzeAndFilter("pornhub.com").shouldBlock)
    }

    @Test fun localAllowlistIsPrivatePersistentAndAppliedByFilterEngine() {
        val domain = "doubleclick.net"
        preferences.setAdBlockingEnabled(true)
        val allowlist = DeviceLearning.allowlist(context)
        assertTrue(allowlist.add(domain))

        val file = File(context.noBackupFilesDir, "allowlist-v1")
        assertTrue(file.exists())
        assertFalse(file.readText().contains(domain))
        assertTrue(DeviceLearning.allowlist(context).contains(domain))
        assertFalse(ContentFilterEngine(context, preferences = preferences)
            .analyzeAndFilter(domain).shouldBlock)

        DeviceLearning.clearAllowlist(context)
        assertFalse(DeviceLearning.allowlist(context).contains(domain))
    }

    @Test fun localBlocklistIsPrivatePersistentAndAppliedByFilterEngine() {
        val domain = "wikipedia.org"
        preferences.setAdBlockingEnabled(false)
        preferences.setAdultBlockingEnabled(false)
        val blocklist = DeviceLearning.blocklist(context)
        assertTrue(blocklist.add(domain))

        val file = File(context.noBackupFilesDir, "blocklist-v1")
        assertTrue(file.exists())
        assertFalse(file.readText().contains(domain))
        assertTrue(DeviceLearning.blocklist(context).contains(domain))
        assertTrue(ContentFilterEngine(context, preferences = preferences)
            .analyzeAndFilter(domain).shouldBlock)

        DeviceLearning.clearBlocklist(context)
        assertFalse(DeviceLearning.blocklist(context).contains(domain))
    }

    @Test fun bootHonorsPersistedUserChoiceAndConsent() {
        shell("appops set com.unblocker.app ACTIVATE_VPN allow")
        var starts = 0
        val receiverContext = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName? {
                starts++
                return intent.component
            }
        }
        preferences.setAutoRestartOnBoot(true)
        preferences.setProtectionEnabled(false)
        BootReceiver().onReceive(receiverContext, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0, starts)
        preferences.setProtectionEnabled(true)
        assertTrue(FilteringPreferences(context).protectionEnabled.value)
        BootReceiver().onReceive(receiverContext, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, starts)
        shell("appops set com.unblocker.app ACTIVATE_VPN deny")
        BootReceiver().onReceive(receiverContext, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, starts)
    }

    @Test fun deniedConsentNeverReportsActiveTunnel() {
        openApp()
        shell("appops set com.unblocker.app ACTIVATE_VPN deny")
        UnblockerVpnService.start(context)
        awaitState(ServiceStatus.ERROR)
        assertFalse(UnblockerVpnService.isServiceActive.value)
    }

    @Test fun establishedTunnelBlocksDnsAndStopsCleanly() {
        openApp()
        shell("appops set com.unblocker.app ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))
        preferences.setAdBlockingEnabled(true)
        preferences.setProtectionEnabled(true)
        UnblockerVpnService.start(context)
        awaitState(ServiceStatus.RUNNING)
        assertTrue(UnblockerVpnService.isServiceActive.value)
        // establish() returns before ConnectivityService finishes publishing routing.
        awaitVpnRouting()
        val question = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
            byteArrayOf(3) + "ads".toByteArray() + byteArrayOf(7) + "example".toByteArray() +
            byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)
        DatagramSocket().use { socket ->
            socket.soTimeout = 5000
            socket.send(DatagramPacket(question, question.size, InetAddress.getByName("10.10.0.1"), 53))
            val response = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(response)
            assertEquals(0x12, response.data[0].toInt())
            assertEquals(0x34, response.data[1].toInt())
            assertArrayEquals(byteArrayOf(0, 0, 0, 0), response.data.copyOfRange(response.length - 4, response.length))
        }
        UnblockerVpnService.stop(context)
        awaitState(ServiceStatus.STOPPED)
        assertFalse(FilteringPreferences(context).protectionEnabled.value)
        // A fresh start must have a working executor/tunnel after the previous run shuts down.
        preferences.setProtectionEnabled(true)
        UnblockerVpnService.start(context)
        awaitState(ServiceStatus.RUNNING)
        assertTrue(UnblockerVpnService.isServiceActive.value)
        UnblockerVpnService.stop(context)
        awaitState(ServiceStatus.STOPPED)
    }
}
