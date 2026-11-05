package com.estia.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Revisión cada minuto: aplica horario, fin de mantenimiento y reaplica políticas. */
object Ticker {
    private const val ACTION = "com.estia.kiosk.TICK"

    fun schedule(ctx: Context, delayMs: Long = 60_000L) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ACTION).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val policy = Policy(context)
        val wasMaintenance = prefs.maintenanceUntil != 0L
        if (policy.isOwner) policy.apply()

        // Terminó el mantenimiento: devolver el kiosco al frente.
        if (wasMaintenance && !prefs.inMaintenance()) {
            prefs.maintenanceUntil = 0L
            try {
                context.startActivity(
                    Intent(context, LauncherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (_: Exception) { }
        }
        Ticker.schedule(context)
    }
}
