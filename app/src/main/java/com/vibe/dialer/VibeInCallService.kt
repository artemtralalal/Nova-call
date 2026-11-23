package com.vibe.dialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class VibeInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call)
        val i = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    CallManager.setCall(null)
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        if (CallManager.currentCall == call) CallManager.setCall(null)
        super.onCallRemoved(call)
    }
}
