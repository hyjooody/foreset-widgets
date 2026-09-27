package com.forestwidgets

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/** 숲 홈: 기본 홈 앱으로 설정하면 이 화면이 홈 화면이 된다 */
class HomeActivity : Activity() {

    data class AppEntry(val pkg: String, val label: String)

    private lateinit var content: View
    private lateinit var drawer: View
    private lateinit var grid: GridView
    private lateinit var search: EditText
    private lateinit var gestures: GestureDetector

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var allApps: List<AppEntry> = emptyList()
    private var filtered: List<AppEntry> = emptyList()
    private val drawerAdapter = DrawerAdapter()
    private var receiverOn = false

    private val iconPx by lazy { dp(56) }

    private val sysReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0) showBattery(level * 100 / scale.coerceAtLeast(1))
                }
                else -> bindDateTiles()
            }
        }
    }

    // ---------------------------------------------------------------- 생명주기

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        setContentView(R.layout.activity_home)

        content = findViewById(R.id.home_content)
        drawer = findViewById(R.id.drawer)
        grid = findViewById(R.id.grid)
        search = findViewById(R.id.search)

        findViewById<View>(R.id.home_root).setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            content.setPadding(0, top, 0, bottom)
            drawer.setPadding(0, top, 0, bottom)
            insets
        }

        findViewById<ClockView>(R.id.mini_clock).ticks = false
        grid.adapter = drawerAdapter
        grid.setOnItemClickListener { _, _, pos, _ -> filtered.getOrNull(pos)?.let { launch(it.pkg) } }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            filtered.getOrNull(pos)?.let { appInfo(it.pkg) }; true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })

        findViewById<View>(R.id.handle).setOnClickListener { openDrawer() }
        findViewById<View>(R.id.t_camera).setOnClickListener {
            tryStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
        findViewById<View>(R.id.t_search).setOnClickListener {
            if (!tryStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", "")))
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        }
        findViewById<View>(R.id.t_calendar).setOnClickListener {
            tryStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        }
        val clockClick = View.OnClickListener { tryStart(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
        findViewById<View>(R.id.t_mini).setOnClickListener(clockClick)
        findViewById<View>(R.id.t_digital).setOnClickListener(clockClick)
        findViewById<View>(R.id.big_clock).setOnClickListener(clockClick)
        findViewById<View>(R.id.t_battery).setOnClickListener {
            tryStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
        findViewById<View>(R.id.t_todo_sum).setOnClickListener { editTodos() }
        findViewById<View>(R.id.todo_title).setOnClickListener { editTodos() }
        findViewById<View>(R.id.t_year).setOnClickListener { homeMenu() }
        content.setOnLongClickListener { homeMenu(); true }

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) < abs(dx)) return false
                if (drawer.visibility != View.VISIBLE && dy < -dp(80) && vy < -600) {
                    openDrawer(); return true
                }
                if (drawer.visibility == View.VISIBLE && dy > dp(120) && vy > 600 && gridAtTop()) {
                    closeDrawer(); return true
                }
                return false
            }
        })

        firstRunWallpaper()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverOn) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(sysReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(sysReceiver, f)
            receiverOn = true
        }
        bindDateTiles()
        bindTodos()
        loadApps()
    }

    override fun onPause() {
        if (receiverOn) { unregisterReceiver(sysReceiver); receiverOn = false }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeDrawer() // 홈 버튼을 누르면 서랍 닫기
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) closeDrawer()
        // 홈 화면에서는 뒤로 가기로 아무 데도 가지 않는다
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------- 화면

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    private fun firstRunWallpaper() {
        val prefs = getSharedPreferences("forest", MODE_PRIVATE)
        if (prefs.getBoolean("wall_applied", false)) return
        prefs.edit().putBoolean("wall_applied", true).apply()
        applyWallpaper(silent = true)
    }

    fun applyWallpaper(silent: Boolean) {
        exec.execute {
            val ok = try {
                val bmp = BitmapFactory.decodeResource(resources, R.drawable.forest_wallpaper)
                WallpaperManager.getInstance(this).setBitmap(bmp, null, true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                true
            } catch (e: Exception) { false }
            if (!silent) main.post { toast(if (ok) "숲 배경화면을 적용했어요" else "배경화면 적용 실패") }
        }
    }

    private fun bindDateTiles() {
        val now = Calendar.getInstance()
        val prev = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val next = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        text(R.id.cal_day, SimpleDateFormat("EEEE", Locale.ENGLISH).format(now.time).uppercase(Locale.ENGLISH))
        text(R.id.cal_prev, prev.get(Calendar.DAY_OF_MONTH).toString())
        text(R.id.cal_today, now.get(Calendar.DAY_OF_MONTH).toString())
        text(R.id.cal_next, next.get(Calendar.DAY_OF_MONTH).toString())
        val day = now.get(Calendar.DAY_OF_YEAR)
        val total = now.getActualMaximum(Calendar.DAY_OF_YEAR)
        text(R.id.year_text, "${day * 100 / total}%")
        text(R.id.year_sub, "${now.get(Calendar.YEAR)}년이 지나간 만큼")
        val bm = getSystemService(BatteryManager::class.java)
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let { if (it in 0..100) showBattery(it) }
    }

    private fun showBattery(pct: Int) {
        findViewById<ImageView>(R.id.battery_ring).setImageBitmap(IconUtil.batteryRing(dp(46), pct))
        text(R.id.battery_text, "$pct%")
    }

    // ---------------------------------------------------------------- 할 일

    private fun bindTodos() {
        val list = findViewById<LinearLayout>(R.id.todo_list)
        list.removeAllViews()
        var left = 0
        for (i in 0 until Store.TODO_COUNT) {
            val t = Store.todoText(this, i)
            if (t.isBlank()) continue
            val done = Store.todoDone(this, i)
            if (!done) left++
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
                setOnClickListener {
                    Store.setTodoDone(this@HomeActivity, i, !done)
                    BaseWidget.refresh(this@HomeActivity, TodoWidget::class.java)
                    bindTodos()
                }
            }
            row.addView(ImageView(this).apply {
                setImageResource(if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            }, LinearLayout.LayoutParams(dp(14), dp(14)))
            row.addView(TextView(this).apply {
                text = t; textSize = 12.5f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (done) 0x80FFFFFF.toInt() else Color.WHITE)
                setPadding(dp(8), 0, 0, 0)
            })
            list.addView(row)
        }
        text(R.id.todo_sum, if (left == 0) "다 했어요!" else "${left}개 남음")
    }

    private fun editTodos() {
        val box = EditText(this).apply {
            setText((0 until Store.TODO_COUNT).map { Store.todoText(this@HomeActivity, it) }
                .filter { it.isNotBlank() }.joinToString("\n"))
            minLines = 5
            gravity = Gravity.TOP
            hint = "한 줄에 하나씩 (최대 5개)"
        }
        val wrap = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box) }
        dialog().setTitle("할 일 목록")
            .setView(wrap)
            .setPositiveButton("저장") { _, _ ->
                val lines = box.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                for (i in 0 until Store.TODO_COUNT) Store.setTodoText(this, i, lines.getOrElse(i) { "" })
                BaseWidget.refresh(this, TodoWidget::class.java)
                bindTodos()
            }
            .setNeutralButton("체크 모두 해제") { _, _ ->
                for (i in 0 until Store.TODO_COUNT) Store.setTodoDone(this, i, false)
                bindTodos()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---------------------------------------------------------------- 앱 목록 / 칸

    private fun loadApps() {
        exec.execute {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(q, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }
            if (!Slots.isInitialized(this)) Slots.assignDefaults(this, apps.map { it.pkg })
            // 아이콘 미리 만들기
            for (group in Slots.GROUPS.keys) {
                for (pkg in Slots.get(this, group).filterNotNull()) {
                    if (group.startsWith("bar")) IconFactory.mono(this, pkg, dp(30))
                    else IconFactory.pastel(this, pkg, iconPx)
                }
            }
            main.post {
                allApps = apps
                bindSlots()
                applyFilter()
            }
            for (a in apps) IconFactory.pastel(this, a.pkg, iconPx)
            main.post { drawerAdapter.notifyDataSetChanged() }
        }
    }

    private fun bindSlots() {
        val installed = allApps.map { it.pkg }.toSet()
        fun ok(pkg: String?) = pkg?.takeIf { it in installed }

        // 앱 박스 4개 (2줄 x 3칸, 작은 파스텔 아이콘)
        for (b in 0 until 4) {
            val box = findViewById<LinearLayout>(resources.getIdentifier("box$b", "id", packageName))
            val group = "box$b"
            val pkgs = Slots.get(this, group)
            box.removeAllViews()
            for (r in 0 until 2) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                for (c in 0 until 3) {
                    val idx = r * 3 + c
                    val pkg = ok(pkgs.getOrNull(idx))
                    val iv = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(5), dp(3), dp(5), dp(3))
                        if (pkg != null) setImageBitmap(IconFactory.pastel(this@HomeActivity, pkg, iconPx))
                    }
                    slotListeners(iv, group, idx, pkg)
                    row.addView(iv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                }
                box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }

        // 원형 바 2개 (단색 흰 아이콘 + 이름)
        for (b in 0 until 2) {
            val bar = findViewById<LinearLayout>(resources.getIdentifier("bar$b", "id", packageName))
            val group = "bar$b"
            val pkgs = Slots.get(this, group)
            bar.removeAllViews()
            for (i in 0 until 4) {
                val pkg = ok(pkgs.getOrNull(i))
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                val circle = FrameLayout(this).apply {
                    setBackgroundResource(R.drawable.bg_circle_glass)
                    if (pkg == null) alpha = 0.35f
                }
                circle.addView(ImageView(this).apply {
                    if (pkg != null) setImageBitmap(IconFactory.mono(this@HomeActivity, pkg, dp(30)))
                }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                cell.addView(circle, LinearLayout.LayoutParams(dp(38), dp(38)))
                cell.addView(TextView(this).apply {
                    text = pkg?.let { p -> allApps.firstOrNull { it.pkg == p }?.label } ?: ""
                    textSize = 8.5f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER
                    setPadding(dp(2), dp(3), dp(2), 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                slotListeners(cell, group, i, pkg)
                bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        // 독 5칸 (큰 파스텔 아이콘)
        val dock = findViewById<LinearLayout>(R.id.dock)
        dock.removeAllViews()
        val dockPkgs = Slots.get(this, "dock")
        for (i in 0 until 5) {
            val pkg = ok(dockPkgs.getOrNull(i))
            val cell = FrameLayout(this)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (pkg != null) setImageBitmap(IconFactory.pastel(this@HomeActivity, pkg, iconPx))
                else { setBackgroundResource(R.drawable.bg_circle_glass); alpha = 0.3f }
            }
            cell.addView(iv, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
            slotListeners(cell, "dock", i, pkg)
            dock.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun slotListeners(v: View, group: String, index: Int, pkg: String?) {
        v.setOnClickListener { if (pkg != null) launch(pkg) else pickApp(group, index) }
        v.setOnLongClickListener { pickApp(group, index); true }
    }

    private fun pickApp(group: String, index: Int) {
        if (allApps.isEmpty()) return
        val labels = arrayOf<CharSequence>("(비우기)") + allApps.map { it.label as CharSequence }
        dialog().setTitle("이 칸에 넣을 앱")
            .setItems(labels) { _, which ->
                Slots.set(this, group, index, if (which == 0) null else allApps[which - 1].pkg)
                bindSlots()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ---------------------------------------------------------------- 서랍

    private fun openDrawer() {
        if (drawer.visibility == View.VISIBLE) return
        search.setText("")
        drawer.alpha = 0f
        drawer.translationY = dp(60).toFloat()
        drawer.visibility = View.VISIBLE
        drawer.animate().alpha(1f).translationY(0f).setDuration(200).start()
        grid.setSelection(0)
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
        drawer.animate().alpha(0f).translationY(dp(60).toFloat()).setDuration(160)
            .withEndAction { drawer.visibility = View.GONE }.start()
    }

    private fun gridAtTop(): Boolean =
        grid.firstVisiblePosition == 0 && (grid.getChildAt(0)?.top ?: 0) >= 0

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase(Locale.getDefault())
        filtered = if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        drawerAdapter.notifyDataSetChanged()
    }

    private inner class DrawerAdapter : BaseAdapter() {
        override fun getCount() = filtered.size
        override fun getItem(position: Int) = filtered[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = (convertView as? LinearLayout) ?: LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ImageView(context), LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(TextView(context).apply {
                    textSize = 11f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    setPadding(dp(2), dp(5), dp(2), 0)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val app = filtered[position]
            (cell.getChildAt(0) as ImageView).setImageBitmap(IconFactory.pastel(this@HomeActivity, app.pkg, iconPx))
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }

    // ---------------------------------------------------------------- 메뉴 / 도우미

    private fun homeMenu() {
        val items = arrayOf<CharSequence>(
            "할 일 목록 편집", "숲 배경화면 다시 적용", "앱 배치 자동으로 다시 하기",
            "숲 위젯 설정 열기", "기본 홈 앱 바꾸기",
        )
        dialog().setTitle("숲 홈").setItems(items) { _, which ->
            when (which) {
                0 -> editTodos()
                1 -> applyWallpaper(silent = false)
                2 -> { Slots.reset(this); IconFactory.clear(); loadApps() }
                3 -> startActivity(Intent(this, MainActivity::class.java))
                4 -> if (!tryStart(Intent(Settings.ACTION_HOME_SETTINGS)))
                    tryStart(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }.show()
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { toast("앱을 열 수 없어요"); return }
        tryStart(i)
    }

    private fun appInfo(pkg: String) {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    private fun tryStart(i: Intent): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)

    private fun text(id: Int, s: String) { findViewById<TextView>(id).text = s }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
