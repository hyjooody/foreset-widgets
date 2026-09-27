package com.forestwidgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val ACTION_REFRESH = "com.forestwidgets.REFRESH"
private const val PI_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

fun dpToPx(ctx: Context, dp: Int) = (dp * ctx.resources.displayMetrics.density).toInt()

abstract class BaseWidget : AppWidgetProvider() {

    abstract fun build(ctx: Context, widgetId: Int): RemoteViews

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { mgr.updateAppWidget(id, build(ctx, id)) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(ctx)
            onUpdate(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, javaClass)))
            return
        }
        super.onReceive(ctx, intent)
    }

    protected fun refreshIntent(ctx: Context, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, javaClass).setAction(ACTION_REFRESH), PI_FLAGS)

    protected fun activityIntent(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(ctx, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PI_FLAGS)

    companion object {
        fun refresh(ctx: Context, cls: Class<out BaseWidget>) {
            ctx.sendBroadcast(Intent(ctx, cls).setAction(ACTION_REFRESH))
        }
    }
}

class DigitalClockWidget : BaseWidget() {
    override fun build(ctx: Context, widgetId: Int) =
        RemoteViews(ctx.packageName, R.layout.widget_digital).apply {
            setOnClickPendingIntent(R.id.root, activityIntent(ctx, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        }
}

class AnalogClockWidget : BaseWidget() {
    override fun build(ctx: Context, widgetId: Int) =
        RemoteViews(ctx.packageName, R.layout.widget_analog).apply {
            setOnClickPendingIntent(R.id.root, activityIntent(ctx, 2, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        }
}

class CalendarWidget : BaseWidget() {
    override fun build(ctx: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_calendar)
        val now = Calendar.getInstance()
        val prev = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val next = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        v.setTextViewText(R.id.day_name, SimpleDateFormat("EEEE", Locale.ENGLISH).format(now.time).uppercase(Locale.ENGLISH))
        v.setTextViewText(R.id.day_prev, prev.get(Calendar.DAY_OF_MONTH).toString())
        v.setTextViewText(R.id.day_today, now.get(Calendar.DAY_OF_MONTH).toString())
        v.setTextViewText(R.id.day_next, next.get(Calendar.DAY_OF_MONTH).toString())
        val uri: Uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").let {
            ContentUris.appendId(it, System.currentTimeMillis()); it.build()
        }
        v.setOnClickPendingIntent(R.id.root, activityIntent(ctx, 3, Intent(Intent.ACTION_VIEW, uri)))
        return v
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        scheduleMidnight(ctx)
    }

    private fun scheduleMidnight(ctx: Context) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5)
        }
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.set(AlarmManager.RTC, cal.timeInMillis, refreshIntent(ctx, 900))
    }
}

class BatteryWidget : BaseWidget() {
    override fun build(ctx: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_battery)
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0).coerceIn(0, 100)
        v.setImageViewBitmap(R.id.battery_ring, IconUtil.batteryRing(dpToPx(ctx, 58), pct))
        v.setTextViewText(R.id.battery_text, "$pct%")
        v.setOnClickPendingIntent(R.id.root, refreshIntent(ctx, 4))
        return v
    }
}

class TodoWidget : BaseWidget() {
    private val rows = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
    private val checks = intArrayOf(R.id.check0, R.id.check1, R.id.check2, R.id.check3, R.id.check4)
    private val texts = intArrayOf(R.id.text0, R.id.text1, R.id.text2, R.id.text3, R.id.text4)

    override fun build(ctx: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_todo)
        for (i in 0 until Store.TODO_COUNT) {
            val text = Store.todoText(ctx, i)
            if (text.isBlank()) { v.setViewVisibility(rows[i], View.GONE); continue }
            v.setViewVisibility(rows[i], View.VISIBLE)
            val done = Store.todoDone(ctx, i)
            v.setTextViewText(texts[i], text)
            v.setTextColor(texts[i], if (done) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
            v.setImageViewResource(checks[i], if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            val toggle = Intent(ctx, TodoWidget::class.java).setAction(ACTION_TOGGLE).putExtra(EXTRA_INDEX, i)
            v.setOnClickPendingIntent(rows[i], PendingIntent.getBroadcast(ctx, 100 + i, toggle, PI_FLAGS))
        }
        v.setOnClickPendingIntent(R.id.todo_title, activityIntent(ctx, 5, Intent(ctx, MainActivity::class.java)))
        return v
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val i = intent.getIntExtra(EXTRA_INDEX, -1)
            if (i in 0 until Store.TODO_COUNT) Store.setTodoDone(ctx, i, !Store.todoDone(ctx, i))
            val mgr = AppWidgetManager.getInstance(ctx)
            onUpdate(ctx, mgr, mgr.getAppWidgetIds(ComponentName(ctx, TodoWidget::class.java)))
            return
        }
        super.onReceive(ctx, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.forestwidgets.TODO_TOGGLE"
        const val EXTRA_INDEX = "index"
    }
}

/** 앱 아이콘 위젯 공통 */
abstract class AppsWidget : BaseWidget() {
    abstract val layout: Int
    abstract val slots: IntArray        // 클릭 영역
    abstract val icons: IntArray        // 아이콘 ImageView
    open val labels: IntArray? = null
    abstract val mono: Boolean
    abstract val iconDp: Int

    override fun build(ctx: Context, widgetId: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        val pkgs = Store.widgetApps(ctx, widgetId)
        val pm = ctx.packageManager
        val size = dpToPx(ctx, iconDp)
        for (i in slots.indices) {
            val pkg = pkgs.getOrNull(i)
            val launch = pkg?.let { pm.getLaunchIntentForPackage(it) }
            if (pkg == null || launch == null) { v.setViewVisibility(slots[i], View.INVISIBLE); continue }
            v.setViewVisibility(slots[i], View.VISIBLE)
            v.setImageViewBitmap(icons[i], IconUtil.appIcon(ctx, pkg, size, mono))
            labels?.let { v.setTextViewText(it[i], IconUtil.label(ctx, pkg)) }
            v.setOnClickPendingIntent(slots[i], activityIntent(ctx, widgetId * 10 + i, launch))
        }
        val empty = pkgs.isEmpty()
        v.setViewVisibility(R.id.hint, if (empty) View.VISIBLE else View.GONE)
        if (empty) {
            val cfg = Intent(ctx, AppPickerActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            v.setOnClickPendingIntent(R.id.hint, activityIntent(ctx, 50000 + widgetId, cfg))
        }
        return v
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { Store.removeWidget(ctx, it) }
    }
}

class AppBoxWidget : AppsWidget() {
    override val layout = R.layout.widget_apps
    override val slots = intArrayOf(R.id.app0, R.id.app1, R.id.app2, R.id.app3, R.id.app4, R.id.app5)
    override val icons = slots
    override val mono = false
    override val iconDp = 26
}

class CircleBarWidget : AppsWidget() {
    override val layout = R.layout.widget_circles
    override val slots = intArrayOf(R.id.c_slot0, R.id.c_slot1, R.id.c_slot2, R.id.c_slot3)
    override val icons = intArrayOf(R.id.c_icon0, R.id.c_icon1, R.id.c_icon2, R.id.c_icon3)
    override val labels: IntArray? = intArrayOf(R.id.c_label0, R.id.c_label1, R.id.c_label2, R.id.c_label3)
    override val mono = true
    override val iconDp = 30
}
