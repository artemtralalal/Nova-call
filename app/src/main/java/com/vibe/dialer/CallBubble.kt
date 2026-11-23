package com.vibe.dialer

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

object CallBubble {
    private var wm: WindowManager? = null
    private var view: TextView? = null

    fun show(context: Context) {
        if (view != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = TextView(app).apply {
            text = "📞"
            textSize = 22f
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xCC1F2937.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 20; y = 300 }
        var downX=0; var downY=0; var sx=0; var sy=0; var moved=false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX.toInt(); downY = e.rawY.toInt(); sx = lp.x; sy = lp.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> { val dx = (e.rawX.toInt()-downX); val dy = (e.rawY.toInt()-downY); if (abs(dx)>8||abs(dy)>8) moved=true; lp.x = sx - dx; lp.y = sy + dy; wm.updateViewLayout(v, lp); true }
                MotionEvent.ACTION_UP -> { if (!moved) { hide(app); app.startActivity(Intent(app, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }; true }
                else -> false
            }
        }
        try { wm.addView(v, lp); this.wm = wm; this.view = v } catch (_: Throwable) {}
    }

    fun hide(context: Context) {
        try { view?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        view = null; wm = null
    }
}
