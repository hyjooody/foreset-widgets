package com.forestwidgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 배경 없는 아날로그 시계 (ticks=false면 작은 점 12개만) */
class ClockView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var ticks = true
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick); post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.94f
        if (r <= 0f) return

        if (ticks) {
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                val long = i % 5 == 0
                val r1 = if (long) r * 0.86f else r * 0.93f
                paint.strokeWidth = if (long) r * 0.035f else r * 0.016f
                paint.color = if (long) 0xE6FFFFFF.toInt() else 0x99FFFFFF.toInt()
                canvas.drawLine(
                    cx + (sin(a) * r1).toFloat(), cy - (cos(a) * r1).toFloat(),
                    cx + (sin(a) * r).toFloat(), cy - (cos(a) * r).toFloat(), paint
                )
            }
        } else {
            fill.color = 0xCCFFFFFF.toInt()
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                canvas.drawCircle(cx + (sin(a) * r * 0.9f).toFloat(), cy - (cos(a) * r * 0.9f).toFloat(),
                    if (i % 3 == 0) r * 0.06f else r * 0.035f, fill)
            }
        }

        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f
        val m = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f
        paint.color = 0xFFFFFFFF.toInt()
        hand(canvas, cx, cy, h * 30f, r * 0.5f, r * 0.075f)
        hand(canvas, cx, cy, m * 6f, r * 0.78f, r * 0.045f)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r * 0.07f, fill)
        fill.color = 0xFF5E7268.toInt()
        canvas.drawCircle(cx, cy, r * 0.03f, fill)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float) {
        val a = Math.toRadians(deg.toDouble())
        paint.strokeWidth = w
        c.drawLine(
            cx - (sin(a) * len * 0.1f).toFloat(), cy + (cos(a) * len * 0.1f).toFloat(),
            cx + (sin(a) * len).toFloat(), cy - (cos(a) * len).toFloat(), paint
        )
    }
}
