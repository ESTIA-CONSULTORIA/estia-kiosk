package com.estia.kiosk

import android.content.Context
import java.security.MessageDigest
import java.util.Calendar

/** Configuración guardada en el propio dispositivo (funciona sin internet). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("kiosk", Context.MODE_PRIVATE)

    // ---- Apps permitidas ----
    var allowed: Set<String>
        get() = sp.getStringSet("allowed", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("allowed", v.toSet()).apply() }

    // ---- Bloqueos de funciones (true = bloqueado) ----
    var blockCamera by bool("blockCamera", true)
    var blockUsb by bool("blockUsb", true)
    var blockBluetooth by bool("blockBluetooth", true)
    var blockWifiConfig by bool("blockWifiConfig", false)
    var blockStatusBar by bool("blockStatusBar", true)
    var blockInstall by bool("blockInstall", true)
    var blockAccounts by bool("blockAccounts", true)
    var blockDebugging by bool("blockDebugging", false)
    var blockDateTime by bool("blockDateTime", true)
    var blockSms by bool("blockSms", true)
    var blockOutgoingCalls by bool("blockOutgoingCalls", true)
    var blockMobileNetworks by bool("blockMobileNetworks", false)

    // ---- Horario ----
    var scheduleEnabled by bool("scheduleEnabled", false)
    var startMinutes: Int
        get() = sp.getInt("startMinutes", 9 * 60)
        set(v) { sp.edit().putInt("startMinutes", v).apply() }
    var endMinutes: Int
        get() = sp.getInt("endMinutes", 23 * 60)
        set(v) { sp.edit().putInt("endMinutes", v).apply() }

    // ---- Mantenimiento temporal (el admin libera el equipo unos minutos) ----
    var maintenanceUntil: Long
        get() = sp.getLong("maintenanceUntil", 0L)
        set(v) { sp.edit().putLong("maintenanceUntil", v).apply() }

    fun inMaintenance() = System.currentTimeMillis() < maintenanceUntil

    /** ¿Estamos dentro del horario de uso? Soporta horarios que cruzan medianoche. */
    fun inSchedule(): Boolean {
        if (!scheduleEnabled) return true
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val s = startMinutes
        val e = endMinutes
        return if (s <= e) now in s until e else (now >= s || now < e)
    }

    // ---- PIN ----
    fun hasPin() = sp.getString("pinHash", null) != null

    fun setPin(pin: String) {
        val salt = System.nanoTime().toString()
        sp.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pinSalt", "") ?: ""
        return hash(salt, pin) == sp.getString("pinHash", null)
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun bool(key: String, def: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }
}
