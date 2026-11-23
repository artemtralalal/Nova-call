package com.vibe.dialer

import android.content.Context

class DialerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("vibe_dialer", Context.MODE_PRIVATE)

    var rightHandMode: Boolean
        get() = prefs.getBoolean("right_hand_mode", false)
        set(v) = prefs.edit().putBoolean("right_hand_mode", v).apply()

    var keypadExpanded: Boolean
        get() = prefs.getBoolean("keypad_expanded", true)
        set(v) = prefs.edit().putBoolean("keypad_expanded", v).apply()

    var navIndex: Int
        get() = prefs.getInt("nav_index", 1).coerceIn(0, 2)
        set(v) = prefs.edit().putInt("nav_index", v.coerceIn(0, 2)).apply()

    var navBubbleX: Float
        get() = prefs.getFloat("nav_bubble_x", -1f)
        set(v) = prefs.edit().putFloat("nav_bubble_x", v).apply()

    var glassIntensity: Int
        get() = prefs.getInt("glass_intensity", 60).coerceIn(0, 100)
        set(v) = prefs.edit().putInt("glass_intensity", v.coerceIn(0, 100)).apply()

    var blurAmount: Int
        get() = prefs.getInt("blur_amount", 50).coerceIn(0, 100)
        set(v) = prefs.edit().putInt("blur_amount", v.coerceIn(0, 100)).apply()

    var buttonScale: Int
        get() = prefs.getInt("button_scale", 100).coerceIn(70, 140)
        set(v) = prefs.edit().putInt("button_scale", v.coerceIn(70, 140)).apply()

    var haptic: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) = prefs.edit().putBoolean("haptic", v).apply()

    var inCallOffsetX: Float
        get() = prefs.getFloat("incall_offset_x", 0f)
        set(v) = prefs.edit().putFloat("incall_offset_x", v).apply()

    var inCallOffsetY: Float
        get() = prefs.getFloat("incall_offset_y", 0f)
        set(v) = prefs.edit().putFloat("incall_offset_y", v).apply()
}
