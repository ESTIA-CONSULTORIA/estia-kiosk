package com.estia.kiosk

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Pantalla de inicio del kiosco: solo muestra las apps que el administrador autorizó.
 * Toque 7 veces seguidas el título para entrar a la configuración (pide PIN).
 */
class LauncherActivity : Activity() {

    private val prefs by lazy { Prefs(this) }
    private val policy by lazy { Policy(this) }
    private var taps = 0
    private var lastTap = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ticker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.hasPin()) {
            // Primera vez: crear PIN y elegir apps.
            startActivity(Intent(this, AdminActivity::class.java))
            return
        }
        policy.apply()
        build()
        if (policy.isOwner && !prefs.inMaintenance()) {
            try { startLockTask() } catch (_: Exception) { }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // El botón atrás no hace nada en el inicio del kiosco.
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1F3A"))
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "Expendio"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
            setOnClickListener {
                val now = System.currentTimeMillis()
                taps = if (now - lastTap < 1500) taps + 1 else 1
                lastTap = now
                if (taps >= 7) {
                    taps = 0
                    startActivity(Intent(this@LauncherActivity, AdminActivity::class.java))
                }
            }
        }
        root.addView(title)

        val apps = if (prefs.inSchedule()) allowedApps() else emptyList()
        when {
            !prefs.inSchedule() -> root.addView(message(
                "Fuera de horario\nDisponible de %s a %s".format(fmt(prefs.startMinutes), fmt(prefs.endMinutes))
            ))
            apps.isEmpty() -> root.addView(message("No hay aplicaciones autorizadas."))
            else -> {
                val grid = GridLayout(this).apply { columnCount = 3 }
                for ((pkg, label) in apps) grid.addView(tile(pkg, label))
                root.addView(grid)
            }
        }
        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#0B1F3A")) })
    }

    /** Apps autorizadas que realmente están instaladas y se pueden abrir. */
    private fun allowedApps(): List<Pair<String, String>> {
        val pm = packageManager
        return prefs.allowed
            .filter { it != packageName && pm.getLaunchIntentForPackage(it) != null }
            .map { pkg ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }
                pkg to label
            }
            .sortedBy { it.second.lowercase() }
    }

    private fun tile(pkg: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = GridLayout.LayoutParams().apply { width = dp(120); height = dp(140) }
            addView(ImageView(context).apply {
                setImageDrawable(try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null })
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 2
            })
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(it) } catch (_: Exception) { }
                }
            }
        }
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(Color.parseColor("#C9D6EA"))
        gravity = Gravity.CENTER
        setPadding(0, dp(48), 0, 0)
    }

    private fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
