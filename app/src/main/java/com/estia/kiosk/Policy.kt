package com.estia.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager

/**
 * Aplica las restricciones reales del sistema (requiere ser Device Owner).
 * Si el equipo no es Device Owner, todo esto se omite y trabaja el Plan B (GuardService).
 */
class Policy(private val ctx: Context) {
    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(ctx, AdminReceiver::class.java)
    private val prefs = Prefs(ctx)

    val isOwner: Boolean get() = dpm.isDeviceOwnerApp(ctx.packageName)

    /** Restricciones de usuario controladas por el admin (true = activar). */
    private fun restrictions(): Map<String, Boolean> {
        val m = linkedMapOf(
            // Siempre activas: evitan escapar o borrar el kiosco.
            UserManager.DISALLOW_FACTORY_RESET to true,
            UserManager.DISALLOW_SAFE_BOOT to true,
            UserManager.DISALLOW_ADD_USER to true,
            UserManager.DISALLOW_UNINSTALL_APPS to true,
            UserManager.DISALLOW_APPS_CONTROL to true, // no borrar datos / forzar detención
            UserManager.DISALLOW_NETWORK_RESET to true,
            // Configurables
            UserManager.DISALLOW_INSTALL_APPS to prefs.blockInstall,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to prefs.blockInstall,
            UserManager.DISALLOW_MODIFY_ACCOUNTS to prefs.blockAccounts,
            UserManager.DISALLOW_USB_FILE_TRANSFER to prefs.blockUsb,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA to prefs.blockUsb,
            UserManager.DISALLOW_BLUETOOTH to prefs.blockBluetooth,
            UserManager.DISALLOW_CONFIG_BLUETOOTH to prefs.blockBluetooth,
            UserManager.DISALLOW_CONFIG_WIFI to prefs.blockWifiConfig,
            UserManager.DISALLOW_DEBUGGING_FEATURES to prefs.blockDebugging,
            UserManager.DISALLOW_SMS to prefs.blockSms,
            UserManager.DISALLOW_OUTGOING_CALLS to prefs.blockOutgoingCalls,
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS to prefs.blockMobileNetworks,
        )
        if (Build.VERSION.SDK_INT >= 28) {
            m[UserManager.DISALLOW_CONFIG_DATE_TIME] = prefs.blockDateTime
        }
        return m
    }

    fun apply() {
        if (!isOwner) return
        if (prefs.inMaintenance()) return

        val open = prefs.inSchedule()
        val allowed = prefs.allowed

        // 1) Apps: suspender todo lo que no esté autorizado (o todo, fuera de horario).
        val launchable = launchablePackages()
        val toSuspend = launchable.filter {
            it != ctx.packageName && !isExempt(it) && (!open || it !in allowed)
        }
        val toRun = launchable.filter { it !in toSuspend }
        safe { dpm.setPackagesSuspended(admin, toRun.toTypedArray(), false) }
        safe { dpm.setPackagesSuspended(admin, toSuspend.toTypedArray(), true) }

        // 2) Modo kiosco (lock task) para las apps autorizadas.
        safe { dpm.setLockTaskPackages(admin, (allowed + ctx.packageName).toTypedArray()) }
        if (Build.VERSION.SDK_INT >= 28) {
            var features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME // botón Inicio vuelve al kiosco
            if (!prefs.blockStatusBar) features = features or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            safe { dpm.setLockTaskFeatures(admin, features) }
        }

        // 3) Restricciones del sistema.
        for ((key, on) in restrictions()) {
            safe { if (on) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key) }
        }
        safe { dpm.setCameraDisabled(admin, prefs.blockCamera) }
        safe { dpm.setStatusBarDisabled(admin, prefs.blockStatusBar) }
        safe { dpm.setKeyguardDisabled(admin, true) }

        // 4) Que "Inicio" siempre abra el kiosco.
        safe {
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(ctx, LauncherActivity::class.java))
        }
    }

    /** Quita todas las restricciones (mantenimiento o desinstalación). */
    fun release() {
        if (!isOwner) return
        safe { dpm.setPackagesSuspended(admin, launchablePackages().toTypedArray(), false) }
        safe { dpm.setLockTaskPackages(admin, arrayOf()) }
        for (key in restrictions().keys) safe { dpm.clearUserRestriction(admin, key) }
        safe { dpm.setCameraDisabled(admin, false) }
        safe { dpm.setStatusBarDisabled(admin, false) }
        safe { dpm.setKeyguardDisabled(admin, false) }
        safe { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName) }
    }

    /** Salida definitiva: libera todo y deja de ser Device Owner. */
    @Suppress("DEPRECATION")
    fun uninstallKiosk() {
        release()
        safe { dpm.clearDeviceOwnerApp(ctx.packageName) }
    }

    fun launchablePackages(): List<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return ctx.packageManager.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName }
            .distinct()
    }

    /** Teclados y componentes que nunca se deben suspender. */
    private fun isExempt(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "android" || p.contains("systemui") || p.contains("inputmethod") ||
            p.contains("keyboard") || p.contains("honeyboard") || p.contains("gboard") ||
            p.contains("latin")
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }
}
