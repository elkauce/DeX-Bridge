package com.elkauce.dualdisplaytest

import android.app.*
import android.content.*
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.*

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var dm: DisplayManager
    private lateinit var status: TextView
    private var presentation: Presentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        status = screen("TELÉFONO / CONTROL", display)
        setContentView(status)
        dm = getSystemService(DisplayManager::class.java)
        dm.registerDisplayListener(this, null)
        showExternal()
    }

    private fun info(d: Display): String {
        val m = DisplayMetrics(); d.getRealMetrics(m)
        val mode = d.mode
        return "Display ID: ${d.displayId}\n${d.name}\n${m.widthPixels} × ${m.heightPixels} · ${m.densityDpi} dpi · ${"%.0f".format(mode.refreshRate)} Hz"
    }

    private fun screen(title: String, d: Display): TextView = TextView(this).apply {
        setBackgroundColor(if (title.startsWith("MONITOR")) Color.rgb(12,18,32) else Color.rgb(245,245,245))
        setTextColor(if (title.startsWith("MONITOR")) Color.WHITE else Color.BLACK)
        gravity = Gravity.CENTER
        textSize = 24f
        text = "$title\n\n${info(d)}"
    }

    private fun externalDisplay(): Display? {
        dm.getDisplays("com.samsung.android.hardware.display.category.DESKTOP")
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }?.let { return it }
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }?.let { return it }
        return dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            .maxByOrNull { it.mode.physicalWidth.toLong() * it.mode.physicalHeight }
    }

    private fun launchOnExternal(packageName: String) {
        val ext = externalDisplay() ?: return
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "$packageName no está instalado", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = ext.displayId }
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir en Display ${ext.displayId}: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun externalDesktop(ext: Display): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(12,18,32))
        }
        root.addView(TextView(this).apply {
            text = "MONITOR INTERACTIVO\n${info(ext)}\n\nProbá el mouse sobre estos botones"
            setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; textSize = 18f; isFocusable = true; setOnClickListener { action() }
            buttons.addView(this, LinearLayout.LayoutParams(0, 90, 1f).apply { setMargins(12,12,12,12) })
        }
        button("CHROME") { launchOnExternal("com.android.chrome") }
        button("YOUTUBE") { launchOnExternal("com.google.android.youtube") }
        button("AJUSTES") {
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = ext.displayId }
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle()) } catch (_: Exception) {}
        }
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun showExternal() {
        val ext = externalDisplay()
        if (ext == null) { status.text = "TELÉFONO / CONTROL\n\nEsperando monitor externo…"; return }
        presentation?.dismiss()
        presentation = object : Presentation(this, ext) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                setContentView(externalDesktop(display))
            }
        }
        try { presentation?.show() } catch (e: Exception) { status.append("\n\nError externo: ${e.message}") }
    }

    override fun onDisplayAdded(displayId: Int) = showExternal()
    override fun onDisplayChanged(displayId: Int) { if (displayId != Display.DEFAULT_DISPLAY && presentation == null) showExternal() }
    override fun onDisplayRemoved(displayId: Int) { if (presentation?.display?.displayId == displayId) { presentation?.dismiss(); presentation = null } }
    override fun onDestroy() { dm.unregisterDisplayListener(this); presentation?.dismiss(); super.onDestroy() }
}
