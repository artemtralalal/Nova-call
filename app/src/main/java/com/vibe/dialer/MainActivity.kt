package com.vibe.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: DialerPrefs
    private lateinit var numberText: TextView
    private lateinit var floatingKeypad: LinearLayout
    private lateinit var showKeypadBtn: ImageButton
    private lateinit var bottomNav: FrameLayout
    private lateinit var navBubble: View
    private lateinit var pagePhone: LinearLayout
    private lateinit var pageContacts: LinearLayout
    private lateinit var pageSettings: ScrollView
    private lateinit var titleText: TextView
    private var navIndex = 1
    private val number = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = DialerPrefs(this)

        numberText = findViewById(R.id.numberText)
        floatingKeypad = findViewById(R.id.floatingKeypad)
        showKeypadBtn = findViewById(R.id.showKeypadBtn)
        bottomNav = findViewById(R.id.bottomNav)
        navBubble = findViewById(R.id.navBubble)
        pagePhone = findViewById(R.id.pagePhone)
        pageContacts = findViewById(R.id.pageContacts)
        pageSettings = findViewById(R.id.pageSettings)
        titleText = findViewById(R.id.titleText)

        buildKeypad()
        setupNav()
        setupSettings()
        setupActions()
        applyRightHandMode()
        navIndex = prefs.navIndex
        selectNav(navIndex, animateBubble = false)
        setKeypadExpanded(prefs.keypadExpanded, animate = false)

        findViewById<TextView>(R.id.defaultDialerHint).setOnClickListener { requestDefaultDialer() }
        maybeRequestDefaultDialerOnStart()
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.hideKeypadBtn).setOnClickListener { setKeypadExpanded(false, animate = true) }
        showKeypadBtn.setOnClickListener { setKeypadExpanded(true, animate = true) }
        findViewById<ImageButton>(R.id.backspaceBtn).setOnClickListener { if (number.isNotEmpty()) { number.deleteAt(number.length - 1); renderNumber() } }
        findViewById<ImageButton>(R.id.backspaceBtn).setOnLongClickListener { number.clear(); renderNumber(); true }
        findViewById<ImageButton>(R.id.callBtn).setOnClickListener { placeCall() }
    }

    private fun buildKeypad() {
        val grid = findViewById<GridLayout>(R.id.keypadGrid)
        grid.removeAllViews()
        val keys = listOf("1","2","3","4","5","6","7","8","9","*","0","#")
        val size = (72 * resources.displayMetrics.density).roundToInt()
        val margin = (6 * resources.displayMetrics.density).roundToInt()
        keys.forEach { k ->
            val b = TextView(this).apply {
                text = k
                gravity = Gravity.CENTER
                textSize = 26f
                setTextColor(0xFFFFFFFF.toInt())
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_key)
                setOnClickListener {
                    if (prefs.haptic) it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    animatePress(it)
                    number.append(k)
                    renderNumber()
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = size
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(margin, margin, margin, margin)
            }
            grid.addView(b, lp)
        }
    }

    private fun renderNumber() { numberText.text = number.toString() }

    private fun setKeypadExpanded(expanded: Boolean, animate: Boolean) {
        prefs.keypadExpanded = expanded
        if (animate) {
            if (expanded) {
                showKeypadBtn.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(150).withEndAction { showKeypadBtn.isVisible = false }.start()
                floatingKeypad.isVisible = true
                floatingKeypad.alpha = 0f; floatingKeypad.translationY = 120f * resources.displayMetrics.density; floatingKeypad.scaleX = 0.95f; floatingKeypad.scaleY = 0.95f
                floatingKeypad.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
            } else {
                floatingKeypad.animate().alpha(0f).translationY(140f * resources.displayMetrics.density).scaleX(0.95f).scaleY(0.95f).setDuration(220)
                    .withEndAction {
                        floatingKeypad.isVisible = false
                        showKeypadBtn.isVisible = true; showKeypadBtn.alpha = 0f; showKeypadBtn.scaleX = 0.7f; showKeypadBtn.scaleY = 0.7f
                        showKeypadBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                    }.start()
            }
        } else {
            floatingKeypad.isVisible = expanded
            floatingKeypad.alpha = 1f; floatingKeypad.translationY = 0f; floatingKeypad.scaleX = 1f; floatingKeypad.scaleY = 1f
            showKeypadBtn.isVisible = !expanded
            showKeypadBtn.alpha = 1f; showKeypadBtn.scaleX = 1f; showKeypadBtn.scaleY = 1f
        }
    }

    private fun setupNav() {
        val labels = listOf(findViewById<TextView>(R.id.navContacts), findViewById<TextView>(R.id.navPhone), findViewById<TextView>(R.id.navSettings))
        bottomNav.post {
            val w = (bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 3f
            val lp = navBubble.layoutParams as FrameLayout.LayoutParams
            lp.width = w.roundToInt(); lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            navBubble.layoutParams = lp
            navBubble.translationX = w * navIndex
        }
        labels.forEachIndexed { i, v -> v.setOnClickListener { animatePress(it); selectNav(i, animateBubble = true) } }

        var downX = 0f; var startTx = 0f; var dragging = false
        navBubble.setOnTouchListener { v, e ->
            val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 3f).coerceAtLeast(1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startTx = v.translationX; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (abs(dx) > 8) dragging = true
                    val max = w * 2
                    v.translationX = (startTx + dx).coerceIn(0f, max)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val idx = ((v.translationX + w / 2) / w).roundToInt().coerceIn(0, 2)
                    selectNav(idx, animateBubble = true)
                    if (!dragging) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun selectNav(index: Int, animateBubble: Boolean) {
        navIndex = index; prefs.navIndex = index
        pagePhone.isVisible = index == 1
        pageContacts.isVisible = index == 0
        pageSettings.isVisible = index == 2
        titleText.text = when (index) { 0 -> "Контакты"; 2 -> "Настройки"; else -> "Телефон" }
        val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 3f)
        if (w > 0) {
            val tx = w * index
            if (animateBubble) navBubble.animate().translationX(tx).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
            else navBubble.translationX = tx
        }
    }

    private fun setupSettings() {
        val right = findViewById<SwitchMaterial>(R.id.rightHandSwitch)
        val haptic = findViewById<SwitchMaterial>(R.id.hapticSwitch)
        val glass = findViewById<SeekBar>(R.id.glassSeek)
        val scale = findViewById<SeekBar>(R.id.scaleSeek)
        right.isChecked = prefs.rightHandMode
        haptic.isChecked = prefs.haptic
        glass.progress = prefs.glassIntensity
        scale.progress = (prefs.buttonScale - 70).coerceIn(0, 70)
        right.setOnCheckedChangeListener { _, c -> prefs.rightHandMode = c; applyRightHandMode() }
        haptic.setOnCheckedChangeListener { _, c -> prefs.haptic = c }
        glass.setOnSeekBarChangeListener(simpleSeek { prefs.glassIntensity = it; applyGlass() })
        scale.setOnSeekBarChangeListener(simpleSeek { prefs.buttonScale = it + 70; buildKeypad() })
        findViewById<Button>(R.id.requestDefaultBtn).setOnClickListener { requestDefaultDialer() }
    }

    private fun applyRightHandMode() {
        val right = prefs.rightHandMode
        val d = resources.displayMetrics.density
        val kpLp = floatingKeypad.layoutParams as FrameLayout.LayoutParams
        val showLp = showKeypadBtn.layoutParams as FrameLayout.LayoutParams
        if (right) {
            kpLp.width = (340 * d).roundToInt(); kpLp.gravity = Gravity.BOTTOM or Gravity.END
            showLp.gravity = Gravity.BOTTOM or Gravity.END; showLp.marginEnd = (20 * d).roundToInt()
            bottomNav.layoutParams = (bottomNav.layoutParams as FrameLayout.LayoutParams).apply { width = (360 * d).roundToInt(); gravity = Gravity.BOTTOM or Gravity.END }
        } else {
            kpLp.width = FrameLayout.LayoutParams.MATCH_PARENT; kpLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            showLp.gravity = Gravity.BOTTOM or Gravity.END; showLp.marginEnd = (20 * d).roundToInt()
            bottomNav.layoutParams = (bottomNav.layoutParams as FrameLayout.LayoutParams).apply { width = FrameLayout.LayoutParams.MATCH_PARENT; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        }
        floatingKeypad.layoutParams = kpLp
        showKeypadBtn.layoutParams = showLp
        bottomNav.requestLayout(); floatingKeypad.requestLayout()
    }

    private fun applyGlass() {
        val alpha = (0x33 + (prefs.glassIntensity * 0x99 / 100)).coerceIn(0x22, 0xCC)
        // Simple glass approximation; real blur can be added with RenderEffect on API 31+.
        floatingKeypad.background?.alpha = alpha
        bottomNav.background?.alpha = alpha
    }

    private fun maybeRequestDefaultDialerOnStart() {
        if (!isDefaultDialer()) requestDefaultDialer()
    }

    private fun isDefaultDialer(): Boolean {
        val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return tm.defaultDialerPackage == packageName
    }

    private fun requestDefaultDialer() {
        if (isDefaultDialer()) { Toast.makeText(this, "Уже по умолчанию", Toast.LENGTH_SHORT).show(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                @Suppress("DEPRECATION")
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 1001)
                return
            }
        }
        val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        startActivity(i)
    }

    private fun placeCall() {
        val num = number.toString().trim()
        if (num.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS), 1002)
            return
        }
        val uri = Uri.parse("tel:${Uri.encode(num)}")
        startActivity(Intent(Intent.ACTION_CALL, uri))
    }

    private fun animatePress(v: View) {
        v.animate().cancel()
        v.scaleX = 0.94f; v.scaleY = 0.94f
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
