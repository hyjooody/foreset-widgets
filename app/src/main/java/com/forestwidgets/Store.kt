package com.forestwidgets

import android.content.Context

object Store {
    private fun p(ctx: Context) = ctx.getSharedPreferences("forest", Context.MODE_PRIVATE)

    const val TODO_COUNT = 5
    private val DEFAULT_TODOS = listOf("물 8잔 마시기", "홈트 40분", "방 청소하기", "영어 단어 50개", "")

    fun todoText(ctx: Context, i: Int): String =
        p(ctx).getString("todo_text_$i", null) ?: DEFAULT_TODOS[i]

    fun setTodoText(ctx: Context, i: Int, text: String) {
        val old = todoText(ctx, i)
        val e = p(ctx).edit().putString("todo_text_$i", text)
        if (old != text) e.putBoolean("todo_done_$i", false)
        e.apply()
    }

    fun todoDone(ctx: Context, i: Int): Boolean = p(ctx).getBoolean("todo_done_$i", false)

    fun setTodoDone(ctx: Context, i: Int, done: Boolean) {
        p(ctx).edit().putBoolean("todo_done_$i", done).commit()
    }

    fun widgetApps(ctx: Context, widgetId: Int): List<String> =
        (p(ctx).getString("apps_$widgetId", "") ?: "").split(",").filter { it.isNotBlank() }

    fun setWidgetApps(ctx: Context, widgetId: Int, pkgs: List<String>) {
        p(ctx).edit().putString("apps_$widgetId", pkgs.joinToString(",")).commit()
    }

    fun removeWidget(ctx: Context, widgetId: Int) {
        p(ctx).edit().remove("apps_$widgetId").apply()
    }
}
