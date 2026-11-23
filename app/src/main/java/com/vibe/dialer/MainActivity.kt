package com.vibe.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: DialerPrefs
    private lateinit var numberText: TextView
    private lateinit var floatingKeypad: LinearLayout
    private lateinit var showKeypadBtn: ImageButton
    private lateinit var bottomNav: FrameLayout
    private lateinit var navBubble: View
    private lateinit var pageRecents: LinearLayout
    private lateinit var pageContacts: LinearLayout
    private lateinit var pagePhone: LinearLayout
    private lateinit var pageSettings: ScrollView
    private lateinit var titleText: TextView
    private lateinit var recentsList: ListView
    private lateinit var contactsList: ListView
    private var navIndex = 2
    private val number = StringBuilder()
    private var recentsFilter = 0 // 0 all,1 missed,2 outgoing,3 rejected

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = DialerPrefs(this)

        numberText = findViewById(R.id.numberText)
        floatingKeypad = findViewById(R.id.floatingKeypad)
        showKeypadBtn = findViewById(R.id.showKeypadBtn)
        bottomNav = findViewById(R.id.bottomNav)
        navBubble = findViewById(R.id.navBubble)
        pageRecents = findViewById(R.id.pageRecents)
        pageContacts = findViewById(R.id.pageContacts)
        pagePhone = findViewById(R.id.pagePhone)
        pageSettings = findViewById(R.id.pageSettings)
        titleText = findViewById(R.id.titleText)
        recentsList = findViewById(R.id.recentsList)
        contactsList = findViewById(R.id.contactsList)

        buildKeypad()
        setupNav()
        setupSettings()
        setupActions()
        setupRecentsFilters()
        applyRightHandMode()
        navIndex = prefs.navIndex
        selectNav(navIndex, animateBubble = false)
        setKeypadExpanded(prefs.keypadExpanded, animate = false)
        findViewById<TextView>(R.id.defaultDialerHint).setOnClickListener { requestDefaultDialer() }
        maybeRequestDefaultDialerOnStart()
        ensurePermissions()
        loadContacts()
        loadRecents()
    }

    override fun onResume() { super.onResume(); loadRecents(); loadContacts() }

    private fun ensurePermissions() {
        val need = mutableListOf<String>()
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) need.add(it)
        }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1002)
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.hideKeypadBtn).setOnClickListener { setKeypadExpanded(false, animate = true) }
        showKeypadBtn.setOnClickListener { setKeypadExpanded(true, animate = true) }
        findViewById<ImageButton>(R.id.backspaceBtn).setOnClickListener { if (number.isNotEmpty()) { number.deleteAt(number.length - 1); renderNumber() } }
        findViewById<ImageButton>(R.id.backspaceBtn).setOnLongClickListener { number.clear(); renderNumber(); true }
        findViewById<ImageButton>(R.id.callBtn).setOnClickListener { placeCall(number.toString()) }
    }

    private fun buildKeypad() {
        val grid = findViewById<GridLayout>(R.id.keypadGrid)
        grid.removeAllViews()
        val keys = listOf("1","2","3","4","5","6","7","8","9","*","0","#")
        val scale = (if (prefs.rightHandMode) prefs.orbitScale else prefs.buttonScale) / 100f
        val size = (64 * resources.displayMetrics.density * scale).roundToInt().coerceAtLeast((48 * resources.displayMetrics.density).roundToInt())
        val margin = (4 * resources.displayMetrics.density).roundToInt()
        keys.forEach { k ->
            val b = TextView(this).apply {
                text = k; gravity = Gravity.CENTER; textSize = 24f * scale.coerceAtLeast(0.85f)
                setTextColor(0xFFFFFFFF.toInt())
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_key)
                setOnClickListener { pressAnim(it); if (prefs.haptic) it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); number.append(k); renderNumber() }
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
        val dy = 140f * resources.displayMetrics.density
        if (animate) {
            if (expanded) {
                showKeypadBtn.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(150).withEndAction { showKeypadBtn.isVisible = false }.start()
                floatingKeypad.isVisible = true; floatingKeypad.alpha = 0f; floatingKeypad.translationY = dy; floatingKeypad.scaleX = 0.95f; floatingKeypad.scaleY = 0.95f
                floatingKeypad.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
            } else {
                floatingKeypad.animate().alpha(0f).translationY(dy).scaleX(0.95f).scaleY(0.95f).setDuration(220).withEndAction {
                    floatingKeypad.isVisible = false
                    showKeypadBtn.isVisible = true; showKeypadBtn.alpha = 0f; showKeypadBtn.scaleX = 0.7f; showKeypadBtn.scaleY = 0.7f
                    showKeypadBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                }.start()
            }
        } else {
            floatingKeypad.isVisible = expanded; floatingKeypad.alpha = 1f; floatingKeypad.translationY = 0f; floatingKeypad.scaleX = 1f; floatingKeypad.scaleY = 1f
            showKeypadBtn.isVisible = !expanded; showKeypadBtn.alpha = 1f; showKeypadBtn.scaleX = 1f; showKeypadBtn.scaleY = 1f
        }
    }

    private fun setupNav() {
        val labels = listOf(findViewById<TextView>(R.id.navRecents), findViewById<TextView>(R.id.navContacts), findViewById<TextView>(R.id.navPhone), findViewById<TextView>(R.id.navSettings))
        bottomNav.post { updateBubble(animate = false) }
        labels.forEachIndexed { i, v -> v.setOnClickListener { pressAnim(it); selectNav(i, animateBubble = true) } }
        var downX = 0f; var startTx = 0f; var dragging = false
        navBubble.setOnTouchListener { v, e ->
            val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 4f).coerceAtLeast(1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startTx = v.translationX; dragging = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = e.rawX - downX; if (abs(dx) > 8) dragging = true; v.translationX = (startTx + dx).coerceIn(0f, w * 3); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val idx = ((v.translationX + w / 2) / w).roundToInt().coerceIn(0, 3); selectNav(idx, animateBubble = true); if (!dragging) v.performClick(); true }
                else -> false
            }
        }
    }

    private fun updateBubble(animate: Boolean) {
        val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 4f)
        if (w <= 0) return
        val lp = navBubble.layoutParams as FrameLayout.LayoutParams
        lp.width = w.roundToInt(); lp.height = FrameLayout.LayoutParams.MATCH_PARENT; navBubble.layoutParams = lp
        val tx = w * navIndex
        if (animate) navBubble.animate().translationX(tx).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start() else navBubble.translationX = tx
    }

    private fun selectNav(index: Int, animateBubble: Boolean) {
        navIndex = index; prefs.navIndex = index
        pageRecents.isVisible = index == 0; pageContacts.isVisible = index == 1; pagePhone.isVisible = index == 2; pageSettings.isVisible = index == 3
        titleText.text = when (index) { 0 -> "Недавние"; 1 -> "Контакты"; 3 -> "Настройки"; else -> "Телефон" }
        bottomNav.post { updateBubble(animateBubble) }
        if (index == 0) loadRecents(); if (index == 1) loadContacts()
    }

    private fun setupRecentsFilters() {
        findViewById<Button>(R.id.filterAll).setOnClickListener { recentsFilter = 0; loadRecents() }
        findViewById<Button>(R.id.filterMissed).setOnClickListener { recentsFilter = 1; loadRecents() }
        findViewById<Button>(R.id.filterOutgoing).setOnClickListener { recentsFilter = 2; loadRecents() }
        findViewById<Button>(R.id.filterRejected).setOnClickListener { recentsFilter = 3; loadRecents() }
    }

    private fun loadRecents() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return
        val items = mutableListOf<String>()
        val nums = mutableListOf<String>()
        val uri = CallLog.Calls.CONTENT_URI
        val proj = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE)
        val sel = when (recentsFilter) {
            1 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE}"
            2 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.OUTGOING_TYPE}"
            3 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.REJECTED_TYPE}"
            else -> null
        }
        contentResolver.query(uri, proj, sel, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            val nI = c.getColumnIndex(CallLog.Calls.NUMBER); val tI = c.getColumnIndex(CallLog.Calls.TYPE); val dI = c.getColumnIndex(CallLog.Calls.DATE)
            var count = 0
            while (c.moveToNext() && count < 100) {
                val num = c.getString(nI) ?: continue
                val type = c.getInt(tI)
                val date = c.getLong(dI)
                val label = when (type) { CallLog.Calls.MISSED_TYPE -> "Пропущенный"; CallLog.Calls.OUTGOING_TYPE -> "Исходящий"; CallLog.Calls.INCOMING_TYPE -> "Входящий"; CallLog.Calls.REJECTED_TYPE -> "Отменённый"; else -> "Звонок" }
                val ds = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(date))
                items.add("$num • $label • $ds")
                nums.add(num)
                count++
            }
        }
        recentsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items).apply { }
        recentsList.setOnItemClickListener { _, _, pos, _ -> number.clear(); number.append(nums.getOrNull(pos) ?: ""); renderNumber(); selectNav(2, true) }
        recentsList.setOnItemLongClickListener { _, _, pos, _ -> placeCall(nums.getOrNull(pos) ?: ""); true }
    }

    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val items = mutableListOf<String>()
        val nums = mutableListOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        contentResolver.query(uri, proj, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { c ->
            val nI = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pI = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var count = 0
            while (c.moveToNext() && count < 200) {
                val name = c.getString(nI) ?: ""; val phone = c.getString(pI) ?: ""
                if (phone.isBlank()) continue
                items.add("$name\n$phone"); nums.add(phone); count++
            }
        }
        contactsList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        contactsList.setOnItemClickListener { _, _, pos, _ -> placeCall(nums.getOrNull(pos) ?: "") }
    }

    private fun setupSettings() {
        val right = findViewById<SwitchMaterial>(R.id.rightHandSwitch)
        val haptic = findViewById<SwitchMaterial>(R.id.hapticSwitch)
        val theme = findViewById<SwitchMaterial>(R.id.themeSwitch)
        val distort = findViewById<SwitchMaterial>(R.id.distortionSwitch)
        val distSeek = findViewById<SeekBar>(R.id.distortionSeek)
        val glass = findViewById<SeekBar>(R.id.glassSeek)
        val scale = findViewById<SeekBar>(R.id.scaleSeek)
        val orbitScale = findViewById<SeekBar>(R.id.orbitScaleSeek)
        val endSoundBtn = findViewById<Button>(R.id.endSoundBtn)
        val shapeBtn = findViewById<Button>(R.id.shapeBtn)
        right.isChecked = prefs.rightHandMode; haptic.isChecked = prefs.haptic; theme.isChecked = prefs.themeMode == "light"
        distort.isChecked = prefs.distortion; distSeek.progress = prefs.distortionAmount
        glass.progress = prefs.glassIntensity; scale.progress = (prefs.buttonScale - 70).coerceIn(0,70); orbitScale.progress = (prefs.orbitScale - 70).coerceIn(0,70)
        fun updEnd() { endSoundBtn.text = "Звук сброса: " + when(prefs.endSound){ "samsung"->"Samsung"; "skype"->"Skype"; "facetime"->"FaceTime"; "none"->"Без звука"; else->"Android" } }
        fun updShape() { shapeBtn.text = "Форма кнопок: " + when(prefs.buttonShape){ "rounded"->"Скруглённая"; "squircle"->"Squircle"; else->"Круг" } }
        updEnd(); updShape()
        right.setOnCheckedChangeListener { _, c -> prefs.rightHandMode = c; applyRightHandMode(); buildKeypad() }
        haptic.setOnCheckedChangeListener { _, c -> prefs.haptic = c }
        theme.setOnCheckedChangeListener { _, c -> prefs.themeMode = if (c) "light" else "dark"; recreate() }
        distort.setOnCheckedChangeListener { _, c -> prefs.distortion = c }
        distSeek.setOnSeekBarChangeListener(simpleSeek { prefs.distortionAmount = it })
        glass.setOnSeekBarChangeListener(simpleSeek { prefs.glassIntensity = it; applyGlass() })
        scale.setOnSeekBarChangeListener(simpleSeek { prefs.buttonScale = it + 70; buildKeypad() })
        orbitScale.setOnSeekBarChangeListener(simpleSeek { prefs.orbitScale = it + 70; buildKeypad(); applyRightHandMode() })
        endSoundBtn.setOnClickListener { val opts = arrayOf("Android (Pixel)","Samsung","Skype","FaceTime","Без звука"); val vals = arrayOf("android","samsung","skype","facetime","none"); android.app.AlertDialog.Builder(this).setTitle("Звук сброса").setItems(opts){_,w-> prefs.endSound = vals[w]; updEnd() }.show() }
        shapeBtn.setOnClickListener { val opts = arrayOf("Круг","Скруглённая","Squircle"); val vals = arrayOf("circle","rounded","squircle"); android.app.AlertDialog.Builder(this).setTitle("Форма кнопок").setItems(opts){_,w-> prefs.buttonShape = vals[w]; updShape(); buildKeypad() }.show() }
        findViewById<Button>(R.id.requestDefaultBtn).setOnClickListener { requestDefaultDialer() }
    }

    private fun applyRightHandMode() {
        val right = prefs.rightHandMode
        val d = resources.displayMetrics.density
        val kpLp = floatingKeypad.layoutParams as FrameLayout.LayoutParams
        val showLp = showKeypadBtn.layoutParams as FrameLayout.LayoutParams
        val navLp = bottomNav.layoutParams as FrameLayout.LayoutParams
        if (right) {
            val scale = prefs.orbitScale / 100f
            kpLp.width = (320 * d * scale).roundToInt().coerceAtMost((resources.displayMetrics.widthPixels - (24 * d).roundToInt()))
            kpLp.gravity = Gravity.BOTTOM or Gravity.END
            kpLp.marginEnd = (12 * d).roundToInt(); kpLp.marginStart = (12 * d).roundToInt()
            showLp.gravity = Gravity.BOTTOM or Gravity.END; showLp.marginEnd = (16 * d).roundToInt()
            navLp.width = (340 * d * scale).roundToInt().coerceAtMost((resources.displayMetrics.widthPixels - (24 * d).roundToInt()))
            navLp.gravity = Gravity.BOTTOM or Gravity.END
            navLp.marginEnd = (12 * d).roundToInt(); navLp.marginStart = (12 * d).roundToInt()
        } else {
            kpLp.width = FrameLayout.LayoutParams.MATCH_PARENT; kpLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            kpLp.marginEnd = (12 * d).roundToInt(); kpLp.marginStart = (12 * d).roundToInt()
            showLp.gravity = Gravity.BOTTOM or Gravity.END; showLp.marginEnd = (16 * d).roundToInt()
            navLp.width = FrameLayout.LayoutParams.MATCH_PARENT; navLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            navLp.marginEnd = (12 * d).roundToInt(); navLp.marginStart = (12 * d).roundToInt()
        }
        floatingKeypad.layoutParams = kpLp; showKeypadBtn.layoutParams = showLp; bottomNav.layoutParams = navLp
        floatingKeypad.requestLayout(); bottomNav.requestLayout(); bottomNav.post { updateBubble(false) }
    }

    private fun applyGlass() {
        val alpha = (0x33 + (prefs.glassIntensity * 0x99 / 100)).coerceIn(0x22, 0xCC)
        floatingKeypad.background?.alpha = alpha; bottomNav.background?.alpha = alpha
    }

    private fun maybeRequestDefaultDialerOnStart() { if (!isDefaultDialer()) requestDefaultDialer() }
    private fun isDefaultDialer(): Boolean { val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager; return tm.defaultDialerPackage == packageName }
    private fun requestDefaultDialer() {
        if (isDefaultDialer()) { Toast.makeText(this, "Уже по умолчанию", Toast.LENGTH_SHORT).show(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                @Suppress("DEPRECATION") startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 1001); return
            }
        }
        startActivity(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName))
    }

    private fun placeCall(numRaw: String) {
        val num = numRaw.trim(); if (num.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG), 1002); return
        }
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(num)}")))
    }

    private fun pressAnim(v: View) {
        v.animate().cancel(); v.scaleX = 0.94f; v.scaleY = 0.94f
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
