package com.forestwidgets

import android.app.Activity
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
        findViewById<Button>(R.id.btn_pastel).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_PASTEL))
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
}
