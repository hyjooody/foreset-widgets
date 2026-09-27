package com.forestwidgets

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** 위젯에 넣을 앱 고르기 + 파스텔 아이콘 만들 앱 고르기 */
class AppPickerActivity : Activity() {

    data class AppItem(val pkg: String, val label: String, val icon: Drawable)

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pastelMode = false
    private var maxSlots = 6
    private var isCircle = false
    private val selected = mutableListOf<String>()
    private var apps: List<AppItem> = emptyList()
    private val adapter = AppAdapter()
    private var doneBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        pastelMode = intent.getStringExtra(EXTRA_MODE) == MODE_PASTEL
        if (!pastelMode) {
            widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
            isCircle = info?.provider?.className == CircleBarWidget::class.java.name
            maxSlots = if (isCircle) 4 else 6
            selected.addAll(Store.widgetApps(this, widgetId))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F3F1EA"))
        }
        root.addView(TextView(this).apply {
            text = if (pastelMode) "아이콘을 만들 앱을 고르세요"
                   else "위젯에 넣을 앱을 순서대로 고르세요 (최대 ${maxSlots}개)"
            textSize = 16f
            setTextColor(Color.parseColor("#3E4B47"))
            setPadding(dp(20), dp(16), dp(20), dp(10))
        })
        val loading = ProgressBar(this)
        root.addView(loading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(40) })
        val list = ListView(this).apply { this.adapter = this@AppPickerActivity.adapter; visibility = View.GONE }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (!pastelMode) {
            doneBtn = Button(this).apply { setOnClickListener { finishWidget() } }
            root.addView(doneBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(16), dp(8), dp(16), dp(16)) })
            updateDone()
        }
        setContentView(root)

        list.setOnItemClickListener { _, _, pos, _ ->
            val app = apps[pos]
            if (pastelMode) { chooseColor(app); return@setOnItemClickListener }
            when {
                selected.contains(app.pkg) -> selected.remove(app.pkg)
                selected.size < maxSlots -> selected.add(app.pkg)
                else -> toast("최대 ${maxSlots}개까지 고를 수 있어요")
            }
            adapter.notifyDataSetChanged()
            updateDone()
        }

        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val loaded = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps = loaded
                loading.visibility = View.GONE
                list.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun updateDone() {
        doneBtn?.text = "완료 (${selected.size}/$maxSlots)"
    }

    private fun finishWidget() {
        Store.setWidgetApps(this, widgetId, selected)
        val mgr = AppWidgetManager.getInstance(this)
        val views = if (isCircle) CircleBarWidget().build(this, widgetId) else AppBoxWidget().build(this, widgetId)
        mgr.updateAppWidget(widgetId, views)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun chooseColor(app: AppItem) {
        val names = IconUtil.PASTELS.map { it.name as CharSequence }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${app.label} 아이콘 색")
            .setItems(names) { _, which -> pinShortcut(app, IconUtil.PASTELS[which]) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun pinShortcut(app: AppItem, pastel: IconUtil.Pastel) {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            toast("이 홈 화면은 바로가기 추가를 지원하지 않아요"); return
        }
        val launch = packageManager.getLaunchIntentForPackage(app.pkg)
        if (launch == null) { toast("이 앱은 열 수 없어요"); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val bmp = IconUtil.pastelIcon(this, app.pkg, pastel)
        val info = ShortcutInfo.Builder(this, "pastel_${app.pkg}_${System.currentTimeMillis()}")
            .setShortLabel(app.label)
            .setIcon(Icon.createWithAdaptiveBitmap(bmp))
            .setIntent(launch)
            .build()
        sm.requestPinShortcut(info, null)
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: makeRow()
            val icon = row.getChildAt(0) as ImageView
            val label = row.getChildAt(1) as TextView
            val badge = row.getChildAt(2) as TextView
            val app = apps[position]
            icon.setImageDrawable(app.icon)
            label.text = app.label
            val idx = selected.indexOf(app.pkg)
            badge.text = if (idx >= 0) "${idx + 1}" else ""
            badge.visibility = if (idx >= 0) View.VISIBLE else View.INVISIBLE
            return row
        }

        private fun makeRow() = LinearLayout(this@AppPickerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(context).apply {
                textSize = 15f; setTextColor(Color.parseColor("#3E4B47")); setPadding(dp(14), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                setBackgroundResource(R.drawable.bg_badge); setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; textSize = 12f
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_PASTEL = "pastel"
    }
}
