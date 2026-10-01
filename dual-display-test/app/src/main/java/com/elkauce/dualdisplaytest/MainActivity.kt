package com.elkauce.dualdisplaytest

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), DisplayManager.DisplayListener {
    private lateinit var dm: DisplayManager
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dm = getSystemService(DisplayManager::class.java)
        dm.registerDisplayListener(this, null)
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 24f
            setBackgroundColor(Color.rgb(245,245,245))
            setTextColor(Color.BLACK)
        }
        setContentView(status)
        updateStatusAndLaunch()
    }

    private fun externalDisplay(): Display? {
        dm.getDisplays("com.samsung.android.hardware.display.category.DESKTOP")
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }?.let { return it }
        dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }?.let { return it }
        return dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
            .maxByOrNull { it.mode.physicalWidth.toLong() * it.mode.physicalHeight }
    }

    private fun info(d: Display): String {
        val m = DisplayMetrics(); d.getRealMetrics(m)
        return "Display ${d.displayId} · ${d.name}\n${m.widthPixels} × ${m.heightPixels} · ${m.densityDpi} dpi · ${"%.0f".format(d.mode.refreshRate)} Hz"
    }

    private fun updateStatusAndLaunch() {
        val ext = externalDisplay()
        if (ext == null) {
            status.text = "TELÉFONO LIBRE\n\nEsperando monitor externo…"
            return
        }
        status.text = "TELÉFONO LIBRE\n\nMonitor detectado:\n${info(ext)}\n\nEl escritorio se abrirá en el monitor."
        launchDesktop(ext)
    }

    private fun launchDesktop(ext: Display) {
        val intent = Intent(this, ExternalDesktopActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .putExtra("targetDisplayId", ext.displayId)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = ext.displayId }
        try { startActivity(intent, options.toBundle()) }
        catch (e: Exception) { status.append("\n\nNo se pudo iniciar escritorio: ${e.javaClass.simpleName}") }
    }

    override fun onDisplayAdded(displayId: Int) = updateStatusAndLaunch()
    override fun onDisplayChanged(displayId: Int) { }
    override fun onDisplayRemoved(displayId: Int) { status.text = "TELÉFONO LIBRE\n\nMonitor desconectado." }
    override fun onDestroy() { dm.unregisterDisplayListener(this); super.onDestroy() }
}

class ExternalDesktopActivity : Activity() {
    private lateinit var dm: DisplayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dm = getSystemService(DisplayManager::class.java)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(buildDesktop())
    }

    private fun currentExternal(): Display? {
        val own = display
        if (own != null && own.displayId != Display.DEFAULT_DISPLAY) return own
        val wanted = intent.getIntExtra("targetDisplayId", -1)
        if (wanted >= 0) dm.getDisplay(wanted)?.let { return it }
        return dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
    }

    private fun launchPackage(packageName: String) {
        val ext = currentExternal() ?: return
        val i = packageManager.getLaunchIntentForPackage(packageName)
        if (i == null) { Toast.makeText(this, "$packageName no está instalado", Toast.LENGTH_SHORT).show(); return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            val opts = ActivityOptions.makeBasic().apply { launchDisplayId = ext.displayId }
            startActivity(i, opts.toBundle())
        } catch (e: Exception) { Toast.makeText(this, "No se pudo abrir: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
    }

    private fun launchSettings() {
        val ext = currentExternal() ?: return
        try {
            val opts = ActivityOptions.makeBasic().apply { launchDisplayId = ext.displayId }
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), opts.toBundle())
        } catch (e: Exception) { Toast.makeText(this, "Ajustes no pudo abrirse aquí", Toast.LENGTH_LONG).show() }
    }

    private fun buildDesktop(): View {
        val ext = currentExternal()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(56,56,56,56); setBackgroundColor(Color.rgb(12,18,32))
        }
        root.addView(TextView(this).apply {
            text = "ESCRITORIO EXTERNO\n${if (ext != null) "Display ${ext.displayId} · ${ext.name}" else "Display externo"}\n\nMové el mouse y elegí una aplicación"
            setTextColor(Color.WHITE); textSize = 26f; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1,0,1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun add(label:String, action:()->Unit) { row.addView(Button(this).apply { text=label; textSize=18f; setOnClickListener{action()} }, LinearLayout.LayoutParams(0,96,1f).apply{setMargins(10,10,10,10)}) }
        add("CHROME") { launchPackage("com.android.chrome") }
        add("YOUTUBE") { launchPackage("com.google.android.youtube") }
        add("AJUSTES") { launchSettings() }
        add("HOME") { recreate() }
        root.addView(row, LinearLayout.LayoutParams(-1,-2))
        return root
    }
}
