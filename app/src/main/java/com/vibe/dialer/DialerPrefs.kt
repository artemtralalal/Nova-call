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
        get() = prefs.getInt("nav_index", 2).coerceIn(0, 4)
        set(v) = prefs.edit().putInt("nav_index", v.coerceIn(0, 4)).apply()

    var glassIntensity: Int
        get() = prefs.getInt("glass_intensity", 60).coerceIn(0, 100)
        set(v) = prefs.edit().putInt("glass_intensity", v.coerceIn(0, 100)).apply()

    var buttonScale: Int
        get() = prefs.getInt("button_scale", 100).coerceIn(70, 140)
        set(v) = prefs.edit().putInt("button_scale", v.coerceIn(70, 140)).apply()

    var orbitScale: Int
        get() = prefs.getInt("orbit_scale", 90).coerceIn(70, 140)
        set(v) = prefs.edit().putInt("orbit_scale", v.coerceIn(70, 140)).apply()

    var haptic: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) = prefs.edit().putBoolean("haptic", v).apply()

    var inCallOffsetX: Float
        get() = prefs.getFloat("incall_offset_x", 0f)
        set(v) = prefs.edit().putFloat("incall_offset_x", v).apply()

    var inCallOffsetY: Float
        get() = prefs.getFloat("incall_offset_y", 0f)
        set(v) = prefs.edit().putFloat("incall_offset_y", v).apply()

    var inCallRight: Boolean
        get() = prefs.getBoolean("incall_right", true)
        set(v) = prefs.edit().putBoolean("incall_right", v).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "dark") ?: "dark" // dark|light
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    var endSound: String
        get() = prefs.getString("end_sound", "android") ?: "android" // android|samsung|skype|none
        set(v) = prefs.edit().putString("end_sound", v).apply()

    var buttonShape: String
        get() = prefs.getString("button_shape", "circle") ?: "circle" // circle|rounded|squircle
        set(v) = prefs.edit().putString("button_shape", v).apply()

    var distortion: Boolean
        get() = prefs.getBoolean("distortion", true)
        set(v) = prefs.edit().putBoolean("distortion", v).apply()

    var distortionAmount: Int
        get() = prefs.getInt("distortion_amount", 40).coerceIn(0, 100)
        set(v) = prefs.edit().putInt("distortion_amount", v.coerceIn(0, 100)).apply()

    var callStyle: String
        get() = prefs.getString("call_style", "vibe") ?: "vibe" // vibe|google|xiaomi|samsung
        set(v) = prefs.edit().putString("call_style", v).apply()

    var profileNumber: String
        get() = prefs.getString("profile_number", "+7 000 000-00-00") ?: "+7 000 000-00-00"
        set(v) = prefs.edit().putString("profile_number", v).apply()

    var profileAvatar: String
        get() = prefs.getString("profile_avatar", "") ?: ""
        set(v) = prefs.edit().putString("profile_avatar", v).apply()

    var profilePendingNumber: String
        get() = prefs.getString("profile_pending_number", "") ?: ""
        set(v) = prefs.edit().putString("profile_pending_number", v).apply()

    var profilePendingUntil: Long
        get() = prefs.getLong("profile_pending_until", 0L)
        set(v) = prefs.edit().putLong("profile_pending_until", v).apply()
}
