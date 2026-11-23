package com.vibe.dialer

import android.telecom.Call
import android.telecom.VideoProfile

object CallManager {
    var currentCall: Call? = null
        private set

    private val listeners = mutableSetOf<(Call?) -> Unit>()
    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) { notifyListeners() }
        override fun onDetailsChanged(call: Call, details: Call.Details) { notifyListeners() }
    }

    fun setCall(call: Call?) {
        currentCall?.unregisterCallback(callback)
        currentCall = call
        call?.registerCallback(callback)
        notifyListeners()
    }

    fun addListener(l: (Call?) -> Unit) { listeners.add(l); l(currentCall) }
    fun removeListener(l: (Call?) -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { val c = currentCall; listeners.toList().forEach { it(c) } }

    fun answer() = currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun hangup() {
        val c = currentCall ?: return
        when (c.state) {
            Call.STATE_RINGING -> c.reject(false, null)
            Call.STATE_DIALING, Call.STATE_CONNECTING -> c.disconnect()
            else -> c.disconnect()
        }
    }
    fun toggleMute() {
        // Mute is handled via InCallService.setMuted in real impl; simplified here.
    }
}
