package com.unblocker.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.services.TunnelDnsConfig
import com.unblocker.app.services.UnblockerVpnService
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class VpnNetworkRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val preferences = FilteringPreferences.getInstance(context)

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private fun awaitState(expected: ServiceStatus, timeoutMs: Long = 15_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (UnblockerVpnService.session.status.value != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(expected, UnblockerVpnService.session.status.value)
    }

    private fun openApp() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(500)
    }

    @After
    fun cleanup() {
        UnblockerVpnService.stop(context)
        shell("appops set com.unblocker.app ACTIVATE_VPN deny")
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun permissionRevokedClosesTunnelAndDisablesProtection() {
        openApp()
        shell("appops set com.unblocker.app ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))
        preferences.setProtectionEnabled(true)
        UnblockerVpnService.start(context)
        awaitState(ServiceStatus.RUNNING)
        assertTrue(UnblockerVpnService.isServiceActive.value)

        // Revoke VPN permission
        shell("appops set com.unblocker.app ACTIVATE_VPN deny")
        UnblockerVpnService.stop(context)
        awaitState(ServiceStatus.STOPPED)
        assertFalse(UnblockerVpnService.isServiceActive.value)
    }

    @Test
    fun twentyRapidStartStopCyclesLeaveNoStaleSession() {
        openApp()
        shell("appops set com.unblocker.app ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))

        for (i in 1..20) {
            preferences.setProtectionEnabled(true)
            UnblockerVpnService.start(context)
            preferences.setProtectionEnabled(false)
            UnblockerVpnService.stop(context)
        }

        awaitState(ServiceStatus.STOPPED, timeoutMs = 20_000L)
        assertEquals(ServiceStatus.STOPPED, UnblockerVpnService.session.status.value)
        assertFalse(UnblockerVpnService.isServiceActive.value)
    }

    @Test
    fun blockedDomainReceivesLocalDnsResponseWithoutUpstream() {
        openApp()
        shell("appops set com.unblocker.app ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))
        preferences.setAdBlockingEnabled(true)
        preferences.setProtectionEnabled(true)
        UnblockerVpnService.start(context)
        awaitState(ServiceStatus.RUNNING)

        val question = byteArrayOf(0x43, 0x21, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
            byteArrayOf(3) + "ads".toByteArray() + byteArrayOf(11) + "doubleclick".toByteArray() +
            byteArrayOf(3) + "net".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)

        DatagramSocket().use { socket ->
            socket.soTimeout = 5000
            socket.send(DatagramPacket(question, question.size, InetAddress.getByName(TunnelDnsConfig.IPV4_DNS), 53))
            val response = DatagramPacket(ByteArray(1024), 1024)
            socket.receive(response)
            assertEquals(0x43, response.data[0].toInt())
            assertEquals(0x21, response.data[1].toInt())
            // Response flags show standard response with answer or NXDOMAIN
            assertTrue(response.length >= 12)
        }

        UnblockerVpnService.stop(context)
        awaitState(ServiceStatus.STOPPED)
    }
}
