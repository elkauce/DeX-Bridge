package com.elkauce.dualdisplaytest

import android.app.*
import android.content.*
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
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

    private fun screen(title: String, d: Display): TextView {
        val m = DisplayMetrics(); d.getRealMetrics(m)
        val mode = d.mode
        return TextView(this).apply {
            setBackgroundColor(if (title.startsWith("MONITOR")) Color.rgb(12,18,32) else Color.rgb(245,245,245))
            setTextColor(if (title.startsWith("MONITOR")) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            textSize = 24f
            text = "$title\n\nDisplay ID: ${d.displayId}\nNombre: ${d.name}\nResolución real: ${m.widthPixels} × ${m.heightPixels}\nModo: ${mode.physicalWidth} × ${mode.physicalHeight}\nDensidad: ${m.densityDpi} dpi (${m.density})\nRefresh: ${"%.2f".format(mode.refreshRate)} Hz\nRotación: ${d.rotation}\nFlags: 0x${d.flags.toString(16)}"
        }
    }

    private fun externalDisplay(): Display? {
        val samsung = dm.getDisplays("com.samsung.android.hardware.display.category.DESKTOP")
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
        if (samsung != null) return samsung
        val presentationDisplays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
        if (presentationDisplays != null) return presentationDisplays
        return dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            .maxByOrNull { it.mode.physicalWidth.toLong() * it.mode.physicalHeight }
    }

    private fun showExternal() {
        val ext = externalDisplay()
        if (ext == null) {
            status.text = screen("TELÉFONO / CONTROL\n\nEsperando monitor externo…", display).text
            return
        }
        presentation?.dismiss()
        presentation = object : Presentation(this, ext) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                setContentView(screen("MONITOR EXTERNO", display))
            }
        }
        try { presentation?.show() } catch (e: Exception) {
            status.append("\n\nError externo: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onDisplayAdded(displayId: Int) = showExternal()
    override fun onDisplayChanged(displayId: Int) { if (displayId != Display.DEFAULT_DISPLAY) showExternal() }
    override fun onDisplayRemoved(displayId: Int) { if (presentation?.display?.displayId == displayId) { presentation?.dismiss(); presentation = null } }
    override fun onDestroy() { dm.unregisterDisplayListener(this); presentation?.dismiss(); super.onDestroy() }
}
