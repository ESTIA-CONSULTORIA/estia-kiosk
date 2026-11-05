package com.estia.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Al volverse Device Owner arrancamos el ciclo de revisión periódica.
        Ticker.schedule(context)
        Policy(context).apply()
    }
}
