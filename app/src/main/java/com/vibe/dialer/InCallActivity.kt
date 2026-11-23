package com.vibe.dialer

import android.media.AudioManager
import android.os.Bundle
import android.telecom.Call
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlin.math.abs

class InCallActivity : AppCompatActivity() {
    private lateinit var callStateText: TextView
    private lateinit var callerText: TextView
    private lateinit var controls: LinearLayout
    private lateinit var answerBtn: ImageButton
    private lateinit var hangupBtn: ImageButton
    private lateinit var muteBtn: ImageButton
    private lateinit var speakerBtn: ImageButton
    private lateinit var prefs: DialerPrefs
    private var muted = false
    private var speaker = false

    private val callListener: (Call?) -> Unit = { runOnUiThread { updateCall(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)
        prefs = DialerPrefs(this)
        callStateText = findViewById(R.id.callStateText)
        callerText = findViewById(R.id.callerText)
        controls = findViewById(R.id.inCallControls)
        answerBtn = findViewById(R.id.answerBtn)
        hangupBtn = findViewById(R.id.hangupBtn)
        muteBtn = findViewById(R.id.muteBtn)
        speakerBtn = findViewById(R.id.speakerBtn)

        controls.translationX = prefs.inCallOffsetX
        controls.translationY = prefs.inCallOffsetY
        setupDrag()
        setupButtons()
    }

    override fun onStart() { super.onStart(); CallManager.addListener(callListener) }
    override fun onStop() { CallManager.removeListener(callListener); super.onStop() }

    private fun setupButtons() {
        answerBtn.setOnClickListener { animatePress(it); CallManager.answer() }
        hangupBtn.setOnClickListener { animatePress(it); hangupAnim { CallManager.hangup(); finish() } }
        muteBtn.setOnClickListener {
            animatePress(it); muted = !muted
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.isMicrophoneMute = muted
            muteBtn.alpha = if (muted) 1f else 0.7f
        }
        speakerBtn.setOnClickListener {
            animatePress(it); speaker = !speaker
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = speaker
            speakerBtn.alpha = if (speaker) 1f else 0.7f
        }
    }

    private fun updateCall(call: Call?) {
        if (call == null) { callStateText.text = "Завершено"; hangupAnim { finish() }; return }
        val num = call.details?.handle?.schemeSpecificPart ?: "Неизвестный номер"
        callerText.text = num
        val state = call.state
        callStateText.text = when (state) {
            Call.STATE_DIALING -> "Набор..."
            Call.STATE_RINGING -> "Входящий звонок"
            Call.STATE_CONNECTING -> "Соединение..."
            Call.STATE_ACTIVE -> "Разговор"
            Call.STATE_HOLDING -> "На удержании"
            Call.STATE_DISCONNECTED -> "Завершено"
            else -> "Звонок"
        }
        answerBtn.isVisible = state == Call.STATE_RINGING
        if (state == Call.STATE_DISCONNECTED) hangupAnim { finish() }
    }

    private fun setupDrag() {
        var downX = 0f; var downY = 0f; var startX = 0f; var startY = 0f; var dragging = false
        controls.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = v.translationX; startY = v.translationY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 8 || abs(dy) > 8) dragging = true
                    if (dragging) { v.translationX = startX + dx; v.translationY = startY + dy }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) { prefs.inCallOffsetX = v.translationX; prefs.inCallOffsetY = v.translationY }
                    else v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun animatePress(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        v.animate().cancel(); v.scaleX = 0.92f; v.scaleY = 0.92f
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }

    private fun hangupAnim(end: () -> Unit) {
        hangupBtn.animate().scaleX(0.7f).scaleY(0.7f).alpha(0.5f).setDuration(120).withEndAction {
            hangupBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).withEndAction(end).start()
        }.start()
    }
}
