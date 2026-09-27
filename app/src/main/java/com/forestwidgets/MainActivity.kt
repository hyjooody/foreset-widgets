package com.forestwidgets

import android.app.Activity
import android.app.role.RoleManager
import android.os.Build
import android.provider.Settings
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {

    private val todoIds = intArrayOf(R.id.todo0, R.id.todo1, R.id.todo2, R.id.todo3, R.id.todo4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        todoIds.forEachIndexed { i, id -> findViewById<EditText>(id).setText(Store.todoText(this, i)) }

        findViewById<Button>(R.id.btn_save_todo).setOnClickListener {
            todoIds.forEachIndexed { i, id ->
                Store.setTodoText(this, i, findViewById<EditText>(id).text.toString().trim())
            }
            BaseWidget.refresh(this, TodoWidget::class.java)
            toast("저장했어요")
        }
        findViewById<Button>(R.id.btn_reset_todo).setOnClickListener {
            for (i in 0 until Store.TODO_COUNT) Store.setTodoDone(this, i, false)
            BaseWidget.refresh(this, TodoWidget::class.java)
            toast("체크를 모두 풀었어요")
        }
        findViewById<Button>(R.id.btn_wall_home).setOnClickListener {
            setWallpaper(WallpaperManager.FLAG_SYSTEM)
        }
        findViewById<Button>(R.id.btn_wall_both).setOnClickListener {
            setWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        }
        findViewById<Button>(R.id.btn_apply_all).setOnClickListener { applyAll() }
        findViewById<Button>(R.id.btn_pastel).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_PASTEL))
        }
    }

    /** 배경화면 + 기본 홈 앱 설정을 한 번에 */
    private fun applyAll() {
        getSharedPreferences("forest", MODE_PRIVATE).edit().putBoolean("wall_applied", true).apply()
        setWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) { goHome(); return }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME)
                    return
                } catch (e: Exception) { }
            }
        }
        openHomeSettings()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_HOME) return
        if (resultCode == RESULT_OK) goHome()
        else {
            toast("설정 화면에서 「숲 홈」을 골라주세요")
            openHomeSettings()
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openHomeSettings() {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) {
            try { startActivity(Intent(action)); return } catch (e: Exception) { }
        }
    }

    private fun setWallpaper(which: Int) {
        toast("적용 중…")
        Thread {
            val ok = try {
                val bmp = BitmapFactory.decodeResource(resources, R.drawable.forest_wallpaper)
                WallpaperManager.getInstance(this).setBitmap(bmp, null, true, which)
                true
            } catch (e: Exception) { false }
            runOnUiThread { toast(if (ok) "배경화면을 바꿨어요" else "배경화면 적용에 실패했어요") }
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_HOME = 71
    }
}
