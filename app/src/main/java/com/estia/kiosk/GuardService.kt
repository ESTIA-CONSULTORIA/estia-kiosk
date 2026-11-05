package com.estia.kiosk

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * PLAN B: si el equipo no se pudo volver Device Owner, este servicio de accesibilidad
 * detecta cualquier app no autorizada y regresa al kiosco al instante.
 * Con Device Owner activo no hace nada (el sistema ya bloquea).
 */
class GuardService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (Policy(this).isOwner) return
        val prefs = Prefs(this)
        if (prefs.inMaintenance()) return
        if (pkg == packageName || isSystemSafe(pkg)) return
        if (prefs.inSchedule() && pkg in prefs.allowed) return

        startActivity(
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun isSystemSafe(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "android" || p.contains("systemui") || p.contains("inputmethod") ||
            p.contains("keyboard") || p.contains("honeyboard") || p.contains("gboard") ||
            p.contains("latin")
    }

    override fun onInterrupt() {}
}
