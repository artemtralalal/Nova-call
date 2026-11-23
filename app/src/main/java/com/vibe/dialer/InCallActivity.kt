package com.vibe.dialer

import android.content.ContentValues
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.telecom.Call
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private lateinit var googleSwipe: View
    private lateinit var swipeKnob: ImageButton
    private lateinit var xiaomiIncoming: View
    private lateinit var minimizeBtn: ImageButton
    private lateinit var shieldSpin: ProgressBar
    private lateinit var shieldIcon: TextView
    private lateinit var shieldCode: TextView
    private lateinit var recordBadge: TextView
    private lateinit var endStatusOverlay: LinearLayout
    private lateinit var endStatusText: TextView
    private lateinit var dynamicBg: DynamicCallBackgroundView
    private var muted = false
    private var speaker = false
    private var expanded = false
    private var ending = false
    private var recording = false
    private var recorder: MediaRecorder? = null
    private var recordPath: String? = null
    private var wasActive = false
    private val securityCode = (1..5).map { ("ABCDEFGHJKLMNPQRSTUVWXYZ23456789").random() }.joinToString("")

    private val callListener: (Call?) -> Unit = { runOnUiThread { updateCall(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
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
            googleSwipe = findViewById(R.id.googleSwipe)
            swipeKnob = findViewById(R.id.swipeKnob)
            xiaomiIncoming = findViewById(R.id.xiaomiIncoming)
            minimizeBtn = findViewById(R.id.minimizeBtn)
            shieldSpin = findViewById(R.id.shieldSpin)
            shieldIcon = findViewById(R.id.shieldIcon)
            shieldCode = findViewById(R.id.shieldCode)
            recordBadge = findViewById(R.id.recordBadge)
            endStatusOverlay = findViewById(R.id.endStatusOverlay)
            endStatusText = findViewById(R.id.endStatusText)
            dynamicBg = findViewById(R.id.dynamicBg)
            findViewById<Button>(R.id.endCloseBtn).setOnClickListener { finish() }
            findViewById<ImageButton>(R.id.recordBtn).setOnClickListener { pressAnim(it); toggleRecording() }

            applyMode()
            setupDrag(controls)
            setupDrag(orbitStrip)
            setupButtons()
            setupStyleControls()
            handleIntent(intent)
        } catch (_: Exception) { finish() }
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

    override fun onStart() { super.onStart(); CallBubble.hide(this); CallManager.addListener(callListener) }
    override fun onStop() { CallManager.removeListener(callListener); super.onStop() }

    private fun setupStyleControls() {
        minimizeBtn.setOnClickListener { pressAnim(it); CallBubble.show(this); moveTaskToBack(true) }
        findViewById<ImageButton>(R.id.xiaomiAnswer).setOnClickListener { pressAnim(it); CallManager.answer() }
        findViewById<ImageButton>(R.id.xiaomiDecline).setOnClickListener { pressAnim(it); doHangup() }

        var downX = 0f; var startX = 0f
        swipeKnob.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startX = v.translationX; true }
                MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - downX).coerceIn(-180f,180f); v.translationX = startX + dx; true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val tx = v.translationX
                    if (tx > 120f) { CallManager.answer() }
                    else if (tx < -120f) { doHangup() }
                    v.animate().translationX(0f).setDuration(180).start(); true
                }
                else -> false
            }
        }
    }

    private fun applyStyleForCall(state: Int) {
        val style = prefs.callStyle
        val incoming = state == Call.STATE_RINGING
        googleSwipe.isVisible = style == "google" && incoming
        xiaomiIncoming.isVisible = style == "xiaomi" && incoming
        if (style == "xiaomi" && incoming) {
            xiaomiIncoming.alpha = 0f; xiaomiIncoming.translationY = 120f
            xiaomiIncoming.animate().alpha(1f).translationY(0f).setDuration(350).setInterpolator(android.view.animation.OvershootInterpolator(1f)).start()
        }
        if (style == "google" || style == "xiaomi") {
            if (incoming) { controls.isVisible = false; orbitStrip.isVisible = false }
        }
    }

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
        if (call == null) {
            callStateText.text = "Завершено"
            if (!ending) {
                ending = true
                val msg = if (wasActive) "Абонент сбросил вызов" else "Абонент не ответил"
                showEndStatus(msg, red = wasActive)
                playEndSound { }
            }
            return
        }
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
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                root.setBackgroundColor(0x00000000)
                dynamicBg.setMode(DynamicCallBackgroundView.Mode.WAITING)
                shieldSpin.isVisible = true; shieldIcon.text = "🛡"; shieldCode.isVisible = false
            }
            Call.STATE_ACTIVE -> {
                if (!wasActive) dynamicBg.showCheck() else dynamicBg.setMode(DynamicCallBackgroundView.Mode.ACTIVE)
                wasActive = true
                root.setBackgroundColor(0x00000000)
                shieldSpin.isVisible = false; shieldIcon.text = "✅"; shieldCode.isVisible = true; shieldCode.text = securityCode
            }
            Call.STATE_RINGING -> {
                root.setBackgroundColor(0x00000000)
                dynamicBg.setMode(DynamicCallBackgroundView.Mode.WAITING)
                shieldSpin.isVisible = true; shieldIcon.text = "🛡"; shieldCode.isVisible = false
            }
            Call.STATE_DISCONNECTED -> {
                root.setBackgroundColor(0x00000000)
                dynamicBg.setMode(if (wasActive) DynamicCallBackgroundView.Mode.ENDED_REJECTED else DynamicCallBackgroundView.Mode.ENDED_NO_ANSWER)
                shieldSpin.isVisible = false; shieldIcon.text = "❌"
            }
            else -> {}
        }
        applyStyleForCall(call.state)
        val style = prefs.callStyle
        val incoming = call.state == Call.STATE_RINGING
        if (!(incoming && (style == "google" || style == "xiaomi"))) {
            googleSwipe.isVisible = false; xiaomiIncoming.isVisible = false
            if (!prefs.rightHandMode) { controls.isVisible = true; orbitStrip.isVisible = false }
        }
        answerBtn.isVisible = call.state == Call.STATE_RINGING && style == "vibe"
        if (call.state == Call.STATE_DISCONNECTED && !ending) {
            ending = true
            val msg = if (wasActive) "Абонент сбросил вызов" else "Абонент не ответил"
            showEndStatus(msg, red = wasActive)
            playEndSound { }
        }
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

    private fun toggleRecording() {
        if (recording) { stopRecording(); return }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 2001); return
            }
            val name = "vibe_call_${System.currentTimeMillis()}.m4a"
            val out = java.io.File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), name)
            recordPath = out.absolutePath
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(recordPath)
            r.prepare(); r.start()
            recorder = r; recording = true
            recordBadge.isVisible = true
            recordBadge.text = "Запись... нажмите ещё раз для остановки"
            findViewById<View>(R.id.incallRoot).setBackgroundColor(0xFF1A1030.toInt())
        } catch (_: Exception) { recording = false; recordBadge.isVisible = false }
    }

    private fun stopRecording() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null; recording = false
        recordBadge.isVisible = false
        // copy to Downloads best-effort
        try {
            val src = recordPath?.let { java.io.File(it) } ?: return
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, src.name)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VibeDialer")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) contentResolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
            } else {
                val dstDir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VibeDialer")
                dstDir.mkdirs(); src.copyTo(java.io.File(dstDir, src.name), overwrite = true)
            }
        } catch (_: Exception) {}
        findViewById<View>(R.id.incallRoot).setBackgroundColor(0xFF102018.toInt())
    }

    private fun showEndStatus(text: String, red: Boolean) {
        controls.isVisible = false; orbitStrip.isVisible = false; googleSwipe.isVisible = false; xiaomiIncoming.isVisible = false
        endStatusOverlay.alpha = 0f; endStatusOverlay.isVisible = true
        endStatusText.text = text
        endStatusText.setTextColor(if (red) 0xFFFF5A6A.toInt() else 0xFFB8860B.toInt())
        val btn = findViewById<Button>(R.id.endCloseBtn)
        btn.setTextColor(if (red) 0xFFFFFFFF.toInt() else 0xFF3A2A00.toInt())
        btn.setBackgroundColor(if (red) 0xFFFF5A6A.toInt() else 0xFFFFCC33.toInt())
        endStatusOverlay.setBackgroundColor(0x88000000.toInt())
        endStatusOverlay.animate().alpha(1f).setStartDelay(900).setDuration(350).start()
    }

    private fun endCallUi(end: () -> Unit) {
        if (recording) stopRecording()
        controls.animate().translationY(200f).alpha(0f).setDuration(220).withEndAction {
            findViewById<View>(R.id.incallRoot).animate().alpha(0.35f).setDuration(180).withEndAction(end).start()
        }.start()
    }
}
