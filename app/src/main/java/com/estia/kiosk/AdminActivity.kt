package com.estia.kiosk

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Panel del administrador, protegido con PIN. Todo se configura aquí, en el propio equipo. */
class AdminActivity : Activity() {

    private val prefs by lazy { Prefs(this) }
    private val policy by lazy { Policy(this) }
    private val appChecks = LinkedHashMap<String, CheckBox>()
    private val switches = LinkedHashMap<Switch, (Boolean) -> Unit>()
    private var failed = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.hasPin()) showPinPrompt() else showSetPin(first = true)
    }

    // ------------------------------------------------------------------ PIN

    private fun showPinPrompt() {
        val col = column()
        col.addView(title("Administrador"))
        val pin = pinField("PIN")
        col.addView(pin)
        col.addView(button("Entrar") {
            if (prefs.checkPin(pin.text.toString())) {
                showMain()
            } else {
                failed++
                Toast.makeText(this, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                if (failed >= 5) finish()
            }
        })
        col.addView(button("Cancelar") { finish() })
        setContentView(scroll(col))
    }

    private fun showSetPin(first: Boolean) {
        val col = column()
        col.addView(title(if (first) "Crear PIN de administrador" else "Cambiar PIN"))
        col.addView(label("Mínimo 4 dígitos. Sin este PIN nadie puede cambiar la configuración."))
        val a = pinField("Nuevo PIN")
        val b = pinField("Repetir PIN")
        col.addView(a)
        col.addView(b)
        col.addView(button("Guardar PIN") {
            val p = a.text.toString()
            when {
                p.length < 4 -> toast("Mínimo 4 dígitos")
                p != b.text.toString() -> toast("Los PIN no coinciden")
                else -> { prefs.setPin(p); toast("PIN guardado"); showMain() }
            }
        })
        if (!first) col.addView(button("Cancelar") { showMain() })
        setContentView(scroll(col))
    }

    // ----------------------------------------------------------------- Main

    private fun showMain() {
        appChecks.clear()
        switches.clear()
        val col = column()
        col.addView(title("Configuración del kiosco"))

        col.addView(label(
            if (policy.isOwner) "✔ Control total activo (Device Owner)"
            else "⚠ Plan B: no es Device Owner. Se usa el vigilante de accesibilidad. " +
                "Actívelo en Ajustes > Accesibilidad > Estia Kiosk – Vigilante."
        ))
        if (!policy.isOwner) {
            col.addView(button("Abrir ajustes de accesibilidad") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }

        // ---- Apps permitidas
        col.addView(section("Aplicaciones permitidas"))
        col.addView(label("Marque solo lo que el personal puede usar. Todo lo demás queda bloqueado."))
        val pm = packageManager
        val firstRun = prefs.allowed.isEmpty()
        policy.launchablePackages()
            .filter { it != packageName }
            .map { pkg ->
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                Triple(pkg, name, defaultAllowed(pkg, name))
            }
            .sortedBy { it.second.lowercase() }
            .forEach { (pkg, name, isDefault) ->
                val cb = CheckBox(this).apply {
                    text = "$name\n$pkg"
                    isChecked = pkg in prefs.allowed || (firstRun && isDefault)
                }
                appChecks[pkg] = cb
                col.addView(cb)
            }

        // ---- Funciones bloqueadas
        col.addView(section("Funciones del equipo"))
        addSwitch(col, "Bloquear cámara", prefs.blockCamera) { prefs.blockCamera = it }
        addSwitch(col, "Bloquear USB / transferencia de archivos", prefs.blockUsb) { prefs.blockUsb = it }
        addSwitch(col, "Bloquear Bluetooth", prefs.blockBluetooth) { prefs.blockBluetooth = it }
        addSwitch(col, "Bloquear cambios de Wi‑Fi", prefs.blockWifiConfig) { prefs.blockWifiConfig = it }
        addSwitch(col, "Bloquear barra de estado y notificaciones", prefs.blockStatusBar) { prefs.blockStatusBar = it }
        addSwitch(col, "Bloquear instalar apps", prefs.blockInstall) { prefs.blockInstall = it }
        addSwitch(col, "Bloquear cuentas (Google, Samsung…)", prefs.blockAccounts) { prefs.blockAccounts = it }
        addSwitch(col, "Bloquear cambio de fecha y hora", prefs.blockDateTime) { prefs.blockDateTime = it }
        addSwitch(col, "Bloquear SMS", prefs.blockSms) { prefs.blockSms = it }
        addSwitch(col, "Bloquear llamadas salientes", prefs.blockOutgoingCalls) { prefs.blockOutgoingCalls = it }
        addSwitch(col, "Bloquear redes móviles", prefs.blockMobileNetworks) { prefs.blockMobileNetworks = it }
        addSwitch(col, "Bloquear depuración USB (ADB)", prefs.blockDebugging) { prefs.blockDebugging = it }

        // ---- Horario
        col.addView(section("Horario de uso"))
        val schedule = addSwitch(col, "Limitar a un horario", prefs.scheduleEnabled) { prefs.scheduleEnabled = it }
        val start = EditText(this).apply { hint = "Inicio HH:mm"; setText(fmt(prefs.startMinutes)); inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME }
        val end = EditText(this).apply { hint = "Fin HH:mm"; setText(fmt(prefs.endMinutes)); inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME }
        col.addView(label("Si el fin es menor que el inicio, cruza la medianoche (ej. 18:00 a 02:00)."))
        col.addView(start)
        col.addView(end)

        // ---- Acciones
        col.addView(section("Acciones"))
        col.addView(button("Guardar y aplicar") {
            val s = parseTime(start.text.toString())
            val e = parseTime(end.text.toString())
            if (s == null || e == null) {
                toast("Horario inválido. Use HH:mm, por ejemplo 09:00")
                return@button
            }
            prefs.startMinutes = s
            prefs.endMinutes = e
            prefs.allowed = appChecks.filter { it.value.isChecked }.keys
            for ((sw, setter) in switches) setter(sw.isChecked)
            policy.apply()
            Ticker.schedule(this)
            toast("Configuración aplicada")
            finish()
        })
        col.addView(button("Modo mantenimiento (15 min)") { startMaintenance() })
        col.addView(button("Cambiar PIN") { showSetPin(first = false) })
        col.addView(button("Desactivar kiosco por completo") { confirmUninstall() })
        col.addView(button("Cerrar sin guardar") { finish() })

        setContentView(scroll(col))
    }

    /** Libera el equipo 15 minutos (cambiar Wi‑Fi, actualizar Soft Restaurant, etc.). */
    private fun startMaintenance() {
        prefs.maintenanceUntil = System.currentTimeMillis() + 15 * 60_000L
        try { stopLockTask() } catch (_: Exception) { }
        policy.release()
        Ticker.schedule(this)
        toast("Mantenimiento: 15 minutos")
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun confirmUninstall() {
        AlertDialog.Builder(this)
            .setTitle("Desactivar kiosco")
            .setMessage("Se quitarán todos los bloqueos y la app dejará de ser administradora del equipo. ¿Continuar?")
            .setPositiveButton("Desactivar") { _, _ ->
                try { stopLockTask() } catch (_: Exception) { }
                prefs.maintenanceUntil = Long.MAX_VALUE
                policy.uninstallKiosk()
                toast("Kiosco desactivado")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // -------------------------------------------------------------- Helpers

    /** Por defecto sugiere Soft Restaurant y la calculadora. */
    private fun defaultAllowed(pkg: String, name: String): Boolean {
        val n = name.lowercase()
        val p = pkg.lowercase()
        return n.contains("soft restaurant") || n.contains("softrestaurant") || p.contains("softrestaurant") ||
            n.contains("calculadora") || n.contains("calculator") || p.contains("calculator")
    }

    private fun addSwitch(col: LinearLayout, text: String, value: Boolean, setter: (Boolean) -> Unit): Switch {
        val sw = Switch(this).apply { this.text = text; isChecked = value; setPadding(0, dp(8), 0, dp(8)) }
        switches[sw] = setter
        col.addView(sw)
        return sw
    }

    private fun parseTime(s: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    }

    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(24))
    }

    private fun scroll(child: LinearLayout) = ScrollView(this).apply {
        addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 24f; setPadding(0, 0, 0, dp(12)) }
    private fun section(t: String) = TextView(this).apply { text = t; textSize = 18f; setPadding(0, dp(24), 0, dp(6)) }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setPadding(0, dp(4), 0, dp(8)) }
    private fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }
    private fun pinField(h: String) = EditText(this).apply {
        hint = h
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
