package com.vibe.dialer

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.telecom.Call
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlin.math.abs
import kotlin.math.roundToInt

class InCallActivity : AppCompatActivity() {
    private lateinit var prefs: DialerPrefs
    private lateinit var callStateText: TextView
    private lateinit var callerText: TextView
    private lateinit var controls: LinearLayout
    private lateinit var orbitStrip: LinearLayout
    private lateinit var extraRow: LinearLayout
    private lateinit var answerBtn: ImageButton
    private lateinit var hangupBtn: ImageButton
    private lateinit var muteBtn: ImageButton
    private lateinit var speakerBtn: ImageButton
    private lateinit var holdBtn: ImageButton
    private lateinit var expandBtn: ImageButton
    private var muted = false
    private var speaker = false
    private var expanded = false
    private var ending = false

    private val callListener: (Call?) -> Unit = { runOnUiThread { updateCall(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incall)
        prefs = DialerPrefs(this)
        callStateText = findViewById(R.id.callStateText)
        callerText = findViewById(R.id.callerText)
        controls = findViewById(R.id.inCallControls)
        orbitStrip = findViewById(R.id.orbitStrip)
        extraRow = findViewById(R.id.extraRow)
        answerBtn = findViewById(R.id.answerBtn)
        hangupBtn = findViewById(R.id.hangupBtn)
        muteBtn = findViewById(R.id.muteBtn)
        speakerBtn = findViewById(R.id.speakerBtn)
        holdBtn = findViewById(R.id.holdBtn)
        expandBtn = findViewById(R.id.expandBtn)

        applyMode()
        setupDrag(controls)
        setupDrag(orbitStrip)
        setupButtons()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        when (i?.getStringExtra("action")) {
            "answer" -> CallManager.answer()
            "hangup" -> doHangup()
        }
    }

    override fun onStart() { super.onStart(); CallManager.addListener(callListener) }
    override fun onStop() { CallManager.removeListener(callListener); super.onStop() }

    private fun applyMode() {
        val right = prefs.inCallRight
        val lp = controls.layoutParams as FrameLayout.LayoutParams
        val stripLp = orbitStrip.layoutParams as FrameLayout.LayoutParams
        if (prefs.rightHandMode) {
            orbitStrip.isVisible = true
            controls.isVisible = false
            lp.gravity = Gravity.CENTER_VERTICAL or if (right) Gravity.END else Gravity.START
            lp.bottomMargin = 0; lp.marginEnd = (8 * resources.displayMetrics.density).roundToInt(); lp.marginStart = (8 * resources.displayMetrics.density).roundToInt()
            stripLp.gravity = Gravity.CENTER_VERTICAL or if (right) Gravity.END else Gravity.START
            orbitStrip.layoutParams = stripLp
            controls.layoutParams = lp
        } else {
            orbitStrip.isVisible = false
            controls.isVisible = true
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (56 * resources.displayMetrics.density).roundToInt()
            controls.layoutParams = lp
        }
    }

    private fun setupButtons() {
        orbitStrip.setOnClickListener { pressAnim(it); showControlsFromStrip() }
        expandBtn.setOnClickListener { pressAnim(it); expanded = !expanded; extraRow.isVisible = expanded; expandBtn.rotation = if (expanded) 180f else 0f }
        answerBtn.setOnClickListener { pressAnim(it); CallManager.answer() }
        hangupBtn.setOnClickListener { pressAnim(it); doHangup() }
        muteBtn.setOnClickListener {
            pressAnim(it); muted = !muted
            applyAudio()
            muteBtn.alpha = if (muted) 1f else 0.75f
        }
        speakerBtn.setOnClickListener {
            pressAnim(it); speaker = !speaker
            applyAudio()
            speakerBtn.alpha = if (speaker) 1f else 0.75f
        }
        holdBtn.setOnClickListener {
            pressAnim(it)
            val c = CallManager.currentCall ?: return@setOnClickListener
            if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold()
        }
        findViewById<ImageButton>(R.id.addCallBtn).setOnClickListener {
            pressAnim(it); CallManager.currentCall?.hold(); startActivity(Intent(Intent.ACTION_DIAL))
        }
        findViewById<ImageButton>(R.id.dialNewBtn).setOnClickListener {
            pressAnim(it); CallManager.hangup(); startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        findViewById<ImageButton>(R.id.mergeBtn).setOnClickListener { pressAnim(it) }
    }

    private fun applyAudio() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isMicrophoneMute = muted
            am.isSpeakerphoneOn = speaker
        } catch (_: Exception) {}
    }

    private fun playEndSound(onDone: () -> Unit) {
        try {
            val res = when (prefs.endSound) {
                "none" -> 0
                "samsung" -> R.raw.end_samsung
                "skype" -> R.raw.end_skype
                "facetime" -> R.raw.end_facetime
                else -> R.raw.end_android
            }
            if (res == 0) { onDone(); return }
            val mp = MediaPlayer.create(applicationContext, res)
            if (mp == null) { onDone(); return }
            mp.setOnCompletionListener { try { it.release() } catch (_: Exception) {}; onDone() }
            mp.setOnErrorListener { p, _, _ -> try { p.release() } catch (_: Exception) {}; onDone(); true }
            mp.start()
        } catch (_: Exception) { onDone() }
    }

    private fun doHangup() {
        if (ending) return; ending = true
        CallManager.hangup()
        endCallUi {
            playEndSound { if (!isFinishing) finish() }
        }
    }

    private fun showControlsFromStrip() {
        orbitStrip.isVisible = false
        controls.isVisible = true
        controls.alpha = 0f; controls.translationX = if (prefs.inCallRight) 120f else -120f
        controls.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
    }

    private fun updateCall(call: Call?) {
        if (call == null) { callStateText.text = "Завершено"; if (!ending) { ending = true; endCallUi { playEndSound { if (!isFinishing) finish() } } }; return }
        callerText.text = call.details?.handle?.schemeSpecificPart ?: "Неизвестный номер"
        callStateText.text = when (call.state) {
            Call.STATE_DIALING -> "Набор..."
            Call.STATE_RINGING -> "Входящий звонок"
            Call.STATE_CONNECTING -> "Соединение..."
            Call.STATE_ACTIVE -> "Разговор"
            Call.STATE_HOLDING -> "На удержании"
            Call.STATE_DISCONNECTED -> "Завершено"
            else -> "Звонок"
        }
        val root = findViewById<View>(R.id.incallRoot)
        when (call.state) {
            Call.STATE_ACTIVE -> root.animate().alpha(1f).setDuration(200).start().also { root.setBackgroundColor(0xFF102018.toInt()) }
            Call.STATE_RINGING, Call.STATE_DIALING, Call.STATE_CONNECTING -> root.setBackgroundColor(0xFF101828.toInt())
            Call.STATE_DISCONNECTED -> root.setBackgroundColor(0xFF2A1014.toInt())
        }
        answerBtn.isVisible = call.state == Call.STATE_RINGING
        if (call.state == Call.STATE_DISCONNECTED && !ending) { ending = true; endCallUi { playEndSound { if (!isFinishing) finish() } } }
    }

    private fun setupDrag(target: View) {
        var downX = 0f; var downY = 0f; var startX = 0f; var startY = 0f; var dragging = false
        target.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = v.translationX; startY = v.translationY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 8 || abs(dy) > 8) dragging = true
                    if (dragging) { v.translationX = startX + dx; v.translationY = startY + dy }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val centerX = v.x + v.width / 2
                        val right = centerX > resources.displayMetrics.widthPixels / 2
                        prefs.inCallRight = right
                        val lp = v.layoutParams as FrameLayout.LayoutParams
                        lp.gravity = Gravity.CENTER_VERTICAL or if (right) Gravity.END else Gravity.START
                        v.layoutParams = lp; v.translationX = 0f
                        prefs.inCallOffsetY = v.translationY
                        if (v == controls && prefs.rightHandMode) { controls.isVisible = false; orbitStrip.isVisible = true; applyMode() }
                    } else v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun pressAnim(v: View) {
        if (prefs.haptic) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        v.animate().cancel(); v.scaleX = 0.92f; v.scaleY = 0.92f; v.alpha = 0.9f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }

    private fun endCallUi(end: () -> Unit) {
        controls.animate().translationY(200f).alpha(0f).setDuration(220).withEndAction {
            findViewById<View>(R.id.incallRoot).animate().alpha(0.35f).setDuration(180).withEndAction(end).start()
        }.start()
    }
}
