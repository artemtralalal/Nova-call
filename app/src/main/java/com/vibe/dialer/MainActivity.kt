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
import android.view.ViewGroup
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
    private lateinit var pageProfile: LinearLayout
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
        pageProfile = findViewById(R.id.pageProfile)
        pageSettings = findViewById(R.id.pageSettings)
        titleText = findViewById(R.id.titleText)
        recentsList = findViewById(R.id.recentsList)
        contactsList = findViewById(R.id.contactsList)

        applyTheme()
        buildKeypad()
        setupNav()
        setupSettings()
        setupActions()
        setupRecentsFilters()
        setupProfile()
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
        findViewById<Button>(R.id.hideNumberBtn).setOnClickListener {
            val ops = arrayOf("Tele2 / МТС / Билайн / МегаФон (Россия) - #31#","T-Mobile - #31#","Vodafone - #31#","AT&T - *67","Verizon - *67")
            val codes = arrayOf("#31#","#31#","#31#","*67","*67")
            android.app.AlertDialog.Builder(this).setTitle("Скрыть номер").setItems(ops){_,w-> number.insert(0, codes[w]); renderNumber() }.show()
        }
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
                setOnLongClickListener {
                    if (k == "0") { if (number.isNotEmpty() && number.last() == '0') number.deleteCharAt(number.length-1); number.append('+'); renderNumber(); true }
                    else false
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
        val labels = listOf(findViewById<TextView>(R.id.navRecents), findViewById<TextView>(R.id.navContacts), findViewById<TextView>(R.id.navPhone), findViewById<TextView>(R.id.navProfile), findViewById<TextView>(R.id.navSettings))
        bottomNav.post { updateBubble(animate = false) }
        labels.forEachIndexed { i, v -> v.setOnClickListener { pressAnim(it); selectNav(i, animateBubble = true) } }
        var downX = 0f; var startTx = 0f; var dragging = false
        navBubble.setOnTouchListener { v, e ->
            val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 5f).coerceAtLeast(1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; startTx = v.translationX; dragging = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = e.rawX - downX; if (abs(dx) > 8) dragging = true; v.translationX = (startTx + dx).coerceIn(0f, w * 4); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val idx = ((v.translationX + w / 2) / w).roundToInt().coerceIn(0, 4); selectNav(idx, animateBubble = true); if (!dragging) v.performClick(); true }
                else -> false
            }
        }
    }

    private fun updateBubble(animate: Boolean) {
        val w = ((bottomNav.width - bottomNav.paddingLeft - bottomNav.paddingRight) / 5f)
        if (w <= 0) return
        val lp = navBubble.layoutParams as FrameLayout.LayoutParams
        lp.width = w.roundToInt(); lp.height = FrameLayout.LayoutParams.MATCH_PARENT; navBubble.layoutParams = lp
        val tx = w * navIndex
        if (animate) navBubble.animate().translationX(tx).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start() else navBubble.translationX = tx
    }

    private fun selectNav(index: Int, animateBubble: Boolean) {
        val old = navIndex
        navIndex = index; prefs.navIndex = index
        val pages = listOf(pageRecents, pageContacts, pagePhone, pageProfile, pageSettings)
        pages.forEachIndexed { i, v ->
            val show = i == index
            if (show && v.visibility != View.VISIBLE) {
                v.isVisible = true
                if (animateBubble) {
                    val dir = if (index >= old) 1 else -1
                    v.translationX = 40f * dir * resources.displayMetrics.density; v.alpha = 0f
                    v.animate().translationX(0f).alpha(1f).setDuration(220).start()
                } else { v.translationX = 0f; v.alpha = 1f }
            } else if (!show) { v.isVisible = false }
        }
        titleText.text = when (index) { 0 -> "Недавние"; 1 -> "Контакты"; 2 -> "Телефон"; 3 -> "Профиль"; else -> "Настройки" }
        bottomNav.post { updateBubble(animateBubble) }
        if (index == 0) loadRecents(); if (index == 1) loadContacts(); if (index == 3) updateProfileUi()
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
        val proj = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME)
        val sel = when (recentsFilter) {
            1 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE}"
            2 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.OUTGOING_TYPE}"
            3 -> "${CallLog.Calls.TYPE}=${CallLog.Calls.REJECTED_TYPE}"
            else -> null
        }
        contentResolver.query(uri, proj, sel, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            val nI = c.getColumnIndex(CallLog.Calls.NUMBER); val tI = c.getColumnIndex(CallLog.Calls.TYPE); val dI = c.getColumnIndex(CallLog.Calls.DATE); val cnI = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            var count = 0
            while (c.moveToNext() && count < 100) {
                val num = c.getString(nI) ?: continue
                val type = c.getInt(tI)
                val date = c.getLong(dI)
                val cached = if (cnI >= 0) c.getString(cnI) else null
                val name = cached?.takeIf { it.isNotBlank() } ?: num
                val label = when (type) { CallLog.Calls.MISSED_TYPE -> "Пропущенный"; CallLog.Calls.OUTGOING_TYPE -> "Исходящий"; CallLog.Calls.INCOMING_TYPE -> "Входящий"; CallLog.Calls.REJECTED_TYPE -> "Отменённый"; else -> "Звонок" }
                val ds = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(date))
                val sub = if (name == num) "$label • $ds" else "$num • $label • $ds"
                items.add("$name\n$sub")
                nums.add(num)
                count++
            }
        }
        recentsList.adapter = themedAdapter(items)
        recentsList.setOnItemClickListener { _, _, pos, _ -> number.clear(); number.append(nums.getOrNull(pos) ?: ""); renderNumber(); selectNav(2, true) }
        recentsList.setOnItemLongClickListener { _, _, pos, _ -> showNumberMenu(nums.getOrNull(pos) ?: ""); true }
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
        contactsList.adapter = themedAdapter(items)
        contactsList.setOnItemClickListener { _, _, pos, _ -> placeCall(nums.getOrNull(pos) ?: "") }
        contactsList.setOnItemLongClickListener { _, _, pos, _ -> showNumberMenu(nums.getOrNull(pos) ?: ""); true }
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
        val callStyleBtn = findViewById<Button>(R.id.callStyleBtn)
        val shapeBtn = findViewById<Button>(R.id.shapeBtn)
        right.isChecked = prefs.rightHandMode; haptic.isChecked = prefs.haptic; theme.isChecked = prefs.themeMode == "light"
        distort.isChecked = prefs.distortion; distSeek.progress = prefs.distortionAmount
        glass.progress = prefs.glassIntensity; scale.progress = (prefs.buttonScale - 70).coerceIn(0,70); orbitScale.progress = (prefs.orbitScale - 70).coerceIn(0,70)
        fun updEnd() { endSoundBtn.text = "Звук сброса: " + when(prefs.endSound){ "samsung"->"Samsung"; "skype"->"Skype"; "facetime"->"FaceTime"; "none"->"Без звука"; else->"Android" } }
        fun updShape() { shapeBtn.text = "Форма кнопок: " + when(prefs.buttonShape){ "rounded"->"Скруглённая"; "squircle"->"Squircle"; else->"Круг" } }
        fun updCallStyle() { callStyleBtn.text = "Стиль звонка: " + when(prefs.callStyle){ "google"->"Google Phone"; "xiaomi"->"Xiaomi"; "samsung"->"Samsung"; else->"Vibe" } }
        updEnd(); updShape(); updCallStyle()
        right.setOnCheckedChangeListener { _, c -> prefs.rightHandMode = c; applyRightHandMode(); buildKeypad() }
        haptic.setOnCheckedChangeListener { _, c -> prefs.haptic = c }
        theme.setOnCheckedChangeListener { _, c -> prefs.themeMode = if (c) "light" else "dark"; recreate() }
        distort.setOnCheckedChangeListener { _, c -> prefs.distortion = c }
        distSeek.setOnSeekBarChangeListener(simpleSeek { prefs.distortionAmount = it })
        glass.setOnSeekBarChangeListener(simpleSeek { prefs.glassIntensity = it; applyGlass() })
        scale.setOnSeekBarChangeListener(simpleSeek { prefs.buttonScale = it + 70; buildKeypad() })
        orbitScale.setOnSeekBarChangeListener(simpleSeek { prefs.orbitScale = it + 70; buildKeypad(); applyRightHandMode() })
        endSoundBtn.setOnClickListener { val opts = arrayOf("Android (Pixel)","Samsung","Skype","FaceTime","Без звука"); val vals = arrayOf("android","samsung","skype","facetime","none"); android.app.AlertDialog.Builder(this).setTitle("Звук сброса").setItems(opts){_,w-> prefs.endSound = vals[w]; updEnd() }.show() }
        callStyleBtn.setOnClickListener { val opts = arrayOf("Vibe","Google Phone","Xiaomi","Samsung"); val vals = arrayOf("vibe","google","xiaomi","samsung"); android.app.AlertDialog.Builder(this).setTitle("Стиль звонка").setItems(opts){_,w-> prefs.callStyle = vals[w]; updCallStyle() }.show() }
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

    private fun setupProfile() {
        findViewById<Button>(R.id.changeAvatarBtn).setOnClickListener {
            val i = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            @Suppress("DEPRECATION") startActivityForResult(i, 2002)
        }
        findViewById<Button>(R.id.changeNumberBtn).setOnClickListener {
            val input = EditText(this).apply { hint = "Новый номер"; setText(prefs.profilePendingNumber.ifBlank { prefs.profileNumber }) }
            android.app.AlertDialog.Builder(this).setTitle("Симуляция номера").setView(input)
                .setPositiveButton("Сменить") { _, _ ->
                    val n = input.text.toString().trim(); if (n.isNotBlank()) { prefs.profilePendingNumber = n; prefs.profilePendingUntil = System.currentTimeMillis() + 2 * 60 * 1000L; updateProfileUi() }
                }.setNegativeButton("Отмена", null).show()
        }
        updateProfileUi()
    }

    private fun updateProfileUi() {
        val numTv = findViewById<TextView>(R.id.profileNumber)
        val timerTv = findViewById<TextView>(R.id.profileTimer)
        val av = findViewById<ImageView>(R.id.profileAvatar)
        if (prefs.profileAvatar.isNotBlank()) try { av.setImageURI(Uri.parse(prefs.profileAvatar)) } catch (_: Exception) {}
        val pending = prefs.profilePendingNumber
        val until = prefs.profilePendingUntil
        val now = System.currentTimeMillis()
        if (pending.isNotBlank() && until > now) {
            val left = (until - now) / 1000
            numTv.text = prefs.profileNumber
            timerTv.text = "Проверка номера... новый номер ${pending} через ${left/60}:${String.format("%02d", left%60)}"
            timerTv.postDelayed({ if (!isFinishing) updateProfileUi() }, 1000)
        } else {
            if (pending.isNotBlank() && until > 0 && now >= until) { prefs.profileNumber = pending; prefs.profilePendingNumber = ""; prefs.profilePendingUntil = 0L }
            numTv.text = prefs.profileNumber
            timerTv.text = "Номер подтверждён (симуляция в приложении)"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2002 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.profileAvatar = uri.toString(); updateProfileUi()
        }
    }

    private fun themedAdapter(items: List<String>): ArrayAdapter<String> {
        val light = prefs.themeMode == "light"
        val text = if (light) 0xFF1A1C22.toInt() else 0xFFFFFFFF.toInt()
        val sub = if (light) 0xFF5A6070.toInt() else 0xCCFFFFFF.toInt()
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                val parts = getItem(position)?.split("\n", limit = 2) ?: listOf("")
                t1.text = parts.getOrElse(0) { "" }
                t2.text = parts.getOrElse(1) { "" }
                t1.setTextColor(text); t2.setTextColor(sub)
                t1.textSize = 16f; t2.textSize = 13f
                v.setPadding(24, 20, 24, 20)
                return v
            }
        }
    }

    private fun applyTheme() {
        val light = prefs.themeMode == "light"
        val root = findViewById<View>(R.id.root)
        root.setBackgroundColor(if (light) 0xFFF2F4FA.toInt() else 0xFF101218.toInt())
        val text = if (light) 0xFF1A1C22.toInt() else 0xFFFFFFFF.toInt()
        val sub = if (light) 0xFF5A6070.toInt() else 0xCCFFFFFF.toInt()
        titleText.setTextColor(text)
        numberText.setTextColor(text)
        findViewById<TextView>(R.id.defaultDialerHint).setTextColor(text)
        listOf(R.id.navRecents,R.id.navContacts,R.id.navPhone,R.id.navSettings).forEach { findViewById<TextView>(it).setTextColor(text) }
        fun walk(v: View) {
            if (v is SwitchMaterial) { v.setTextColor(text); return@walk }
            if (v is Button) return@walk
            if (v is TextView) v.setTextColor(text)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(findViewById(R.id.pageSettings))
        walk(findViewById(R.id.pageRecents))
        walk(findViewById(R.id.pageContacts))
        walk(findViewById(R.id.pagePhone))
        findViewById<View>(R.id.floatingKeypad).alpha = 1f
        findViewById<View>(R.id.bottomNav).alpha = 1f
        val grid = findViewById<GridLayout>(R.id.keypadGrid)
        for (i in 0 until grid.childCount) { (grid.getChildAt(i) as? TextView)?.setTextColor(text) }
        findViewById<View>(R.id.navBubble).alpha = if (light) 0.35f else 1f
        val div = if (light) 0x22000000 else 0x22FFFFFF
        recentsList.divider = android.graphics.drawable.ColorDrawable(div); recentsList.dividerHeight = (1 * resources.displayMetrics.density).toInt()
        contactsList.divider = android.graphics.drawable.ColorDrawable(div); contactsList.dividerHeight = (1 * resources.displayMetrics.density).toInt()
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

    private fun addContact(num: String) {
        val i = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, num)
        }
        startActivity(i)
    }

    private fun showNumberMenu(num: String) {
        val opts = arrayOf("Позвонить", "Добавить контакт", "Скопировать номер")
        android.app.AlertDialog.Builder(this).setTitle(num).setItems(opts) { _, w ->
            when (w) { 0 -> placeCall(num); 1 -> addContact(num); 2 -> { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("number", num)); Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show() } }
        }.show()
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
