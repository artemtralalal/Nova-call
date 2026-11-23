package com.vibe.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

class VibeInCallService : InCallService() {
    private val CH = "vibe_calls"

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call)
        ensureChannel()
        updateNotification(call)
        showUi(call)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                updateNotification(c)
                if (state == Call.STATE_DISCONNECTED) {
                    CallManager.setCall(null)
                    cancelNotification()
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        if (CallManager.currentCall == call) { CallManager.setCall(null); cancelNotification() }
        super.onCallRemoved(call)
    }

    private fun showUi(call: Call) {
        val i = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CH, "Звонки", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Входящие и активные звонки"
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun updateNotification(call: Call) {
        val nm = getSystemService(NotificationManager::class.java)
        val num = call.details?.handle?.schemeSpecificPart ?: "Неизвестный номер"
        val state = call.state
        val openIntent = PendingIntent.getActivity(this, 1, Intent(this, InCallActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answerIntent = PendingIntent.getActivity(this, 2, Intent(this, InCallActivity::class.java).putExtra("action","answer"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hangupIntent = PendingIntent.getActivity(this, 3, Intent(this, InCallActivity::class.java).putExtra("action","hangup"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val person = Person.Builder().setName(num).build()
        val builder = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(num)
            .setContentText(when(state){ Call.STATE_RINGING->"Входящий звонок"; Call.STATE_ACTIVE->"Разговор"; Call.STATE_HOLDING->"На удержании"; Call.STATE_DIALING->"Набор..."; else->"Звонок" })
            .setContentIntent(openIntent)
            .setOngoing(state != Call.STATE_DISCONNECTED)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (state == Call.STATE_RINGING) {
            builder.setFullScreenIntent(openIntent, true)
            builder.addAction(android.R.drawable.ic_menu_call, "Ответить", answerIntent)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", hangupIntent)
            if (Build.VERSION.SDK_INT >= 31) {
                try { builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, hangupIntent, answerIntent)) } catch (_: Throwable) {}
            }
        } else {
            builder.setFullScreenIntent(openIntent, true)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Сбросить", hangupIntent)
            if (Build.VERSION.SDK_INT >= 31) {
                try { builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupIntent)) } catch (_: Throwable) {}
            }
        }
        val n = builder.build()
        try {
            if (state == Call.STATE_RINGING || state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_HOLDING) {
                nm.notify(1001, n)
            } else {
                nm.cancel(1001)
            }
        } catch (_: Throwable) {
            try {
                val simple = NotificationCompat.Builder(this, CH)
                    .setSmallIcon(android.R.drawable.ic_menu_call)
                    .setContentTitle(num)
                    .setContentText("Звонок")
                    .setContentIntent(openIntent)
                    .setOngoing(true)
                    .build()
                nm.notify(1001, simple)
            } catch (_: Throwable) {}
        }
    }

    private fun cancelNotification() {
        try { getSystemService(NotificationManager::class.java).cancel(1001) } catch (_: Exception) {}
    }
}
