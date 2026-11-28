package com.unblocker.app.receivers

import android.content.Intent
import com.unblocker.app.services.BootPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies [BootReceiver] and [BootPolicy] restart behavior for boot, quick-boot,
 * and `MY_PACKAGE_REPLACED` broadcasts as well as manifest security constraints.
 */
class BootReceiverTest {
    private val receiver = BootReceiver()

    /**
     * Verifies `BOOT_COMPLETED` restarts VPN and schedules health check when eligible.
     */
    @Test
    fun bootCompletedStartsVpnWhenEligible() {
        var started = false
        var failed = false
        var healthScheduled = false

        val handled =
            receiver.handleRestart(
                action = Intent.ACTION_BOOT_COMPLETED,
                autoRestartOnBoot = true,
                protectionEnabled = true,
                hasVpnPermission = true,
                startVpn = { started = true },
                onStartFailed = { failed = true },
                scheduleHealthCheck = { healthScheduled = true },
            )

        assertTrue(handled)
        assertTrue(started)
        assertFalse(failed)
        assertTrue(healthScheduled)
    }

    /**
     * Verifies OEM `QUICKBOOT_POWERON` restarts VPN and schedules health check when eligible.
     */
    @Test
    fun quickBootStartsVpnWhenEligible() {
        var started = false
        var healthScheduled = false

        val handled =
            receiver.handleRestart(
                action = BootPolicy.ACTION_QUICKBOOT_POWERON,
                autoRestartOnBoot = true,
                protectionEnabled = true,
                hasVpnPermission = true,
                startVpn = { started = true },
                onStartFailed = {},
                scheduleHealthCheck = { healthScheduled = true },
            )

        assertTrue(handled)
        assertTrue(started)
        assertTrue(healthScheduled)
    }

    /**
     * Verifies `MY_PACKAGE_REPLACED` restarts VPN and schedules health check after app update.
     */
    @Test
    fun packageReplacedStartsVpnWhenEligible() {
        var started = false
        var failed = false
        var healthScheduled = false

        val handled =
            receiver.handleRestart(
                action = Intent.ACTION_MY_PACKAGE_REPLACED,
                autoRestartOnBoot = true,
                protectionEnabled = true,
                hasVpnPermission = true,
                startVpn = { started = true },
                onStartFailed = { failed = true },
                scheduleHealthCheck = { healthScheduled = true },
            )

        assertTrue(handled)
        assertTrue(started)
        assertFalse(failed)
        assertTrue(healthScheduled)
    }

    /**
     * Verifies missing VPN consent suppresses background restart and never schedules health work.
     */
    @Test
    fun restartIgnoredWhenVpnConsentMissing() {
        var started = false
        var healthScheduled = false

        val actions =
            listOf(
                Intent.ACTION_BOOT_COMPLETED,
                BootPolicy.ACTION_QUICKBOOT_POWERON,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )

        actions.forEach { action ->
            val handled =
                receiver.handleRestart(
                    action = action,
                    autoRestartOnBoot = true,
                    protectionEnabled = true,
                    hasVpnPermission = false,
                    startVpn = { started = true },
                    onStartFailed = {},
                    scheduleHealthCheck = { healthScheduled = true },
                )
            assertFalse("Expected $action to skip when VPN consent is missing", handled)
        }
        assertFalse(started)
        assertFalse(healthScheduled)
    }

    /**
     * Verifies disabled user intent (`protectionEnabled = false` or `autoRestartOnBoot = false`)
     * prevents automatic restart across all supported broadcast actions.
     */
    @Test
    fun restartIgnoredWhenIntentOrAutoRestartDisabled() {
        var started = false
        var healthScheduled = false

        val disabledPairs = listOf(false to true, true to false, false to false)
        val actions =
            listOf(
                Intent.ACTION_BOOT_COMPLETED,
                BootPolicy.ACTION_QUICKBOOT_POWERON,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )

        actions.forEach { action ->
            disabledPairs.forEach { (autoRestart, protection) ->
                val handled =
                    receiver.handleRestart(
                        action = action,
                        autoRestartOnBoot = autoRestart,
                        protectionEnabled = protection,
                        hasVpnPermission = true,
                        startVpn = { started = true },
                        onStartFailed = {},
                        scheduleHealthCheck = { healthScheduled = true },
                    )
                assertFalse(handled)
            }
        }
        assertFalse(started)
        assertFalse(healthScheduled)
    }

    /**
     * Verifies unrelated or null actions are ignored completely.
     */
    @Test
    fun unrelatedOrNullActionIgnored() {
        var started = false
        listOf(null, "", "android.intent.action.AIRPLANE_MODE").forEach { action ->
            val handled =
                receiver.handleRestart(
                    action = action,
                    autoRestartOnBoot = true,
                    protectionEnabled = true,
                    hasVpnPermission = true,
                    startVpn = { started = true },
                    onStartFailed = {},
                    scheduleHealthCheck = {},
                )
            assertFalse(handled)
        }
        assertFalse(started)
    }

    /**
     * Verifies foreground-service launch failures mark session failed while still scheduling health checks.
     */
    @Test
    fun startExceptionMarksFailureAndSchedulesHealthCheck() {
        var failed = false
        var healthScheduled = false

        val handled =
            receiver.handleRestart(
                action = Intent.ACTION_MY_PACKAGE_REPLACED,
                autoRestartOnBoot = true,
                protectionEnabled = true,
                hasVpnPermission = true,
                startVpn = { throw IllegalStateException("Background FGS blocked") },
                onStartFailed = { failed = true },
                scheduleHealthCheck = { healthScheduled = true },
            )

        assertTrue(handled)
        assertTrue(failed)
        assertTrue(healthScheduled)
    }

    /**
     * Verifies `AndroidManifest.xml` keeps `BootReceiver` non-exported and declares all three actions.
     */
    @Test
    fun manifestDeclaresExpectedReceiverFilterAndNotExported() {
        val candidates =
            listOf(
                File("app/src/main/AndroidManifest.xml"),
                File("src/main/AndroidManifest.xml"),
            )
        val manifest = candidates.firstOrNull { it.isFile } ?: error("AndroidManifest.xml not found")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val receivers = doc.getElementsByTagName("receiver")

        var exportedAttr: String? = null
        val declaredActions = mutableSetOf<String>()

        for (i in 0 until receivers.length) {
            val node = receivers.item(i)
            val name = node.attributes?.getNamedItem("android:name")?.nodeValue
            if (name == ".receivers.BootReceiver") {
                exportedAttr = node.attributes?.getNamedItem("android:exported")?.nodeValue
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeName == "intent-filter") {
                        val filterChildren = child.childNodes
                        for (k in 0 until filterChildren.length) {
                            val actionNode = filterChildren.item(k)
                            if (actionNode.nodeName == "action") {
                                actionNode.attributes?.getNamedItem("android:name")?.nodeValue?.let {
                                    declaredActions.add(it)
                                }
                            }
                        }
                    }
                }
            }
        }

        assertEquals("false", exportedAttr)
        assertTrue(declaredActions.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(declaredActions.contains("android.intent.action.QUICKBOOT_POWERON"))
        assertTrue(declaredActions.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }
}
