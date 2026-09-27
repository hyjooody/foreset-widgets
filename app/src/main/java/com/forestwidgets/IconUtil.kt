package com.forestwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

object IconUtil {

    data class Pastel(val name: String, val bg: Int, val glyph: Int)

    val PASTELS = listOf(
        Pastel("살구 주황", Color.parseColor("#C99A66"), Color.WHITE),
        Pastel("세이지 그린", Color.parseColor("#7D8F86"), Color.parseColor("#F4EFE1")),
        Pastel("라벤더", Color.parseColor("#8C88BE"), Color.WHITE),
        Pastel("연두", Color.parseColor("#BCC98C"), Color.parseColor("#FBF7EA")),
        Pastel("크림", Color.parseColor("#F4F1E6"), Color.parseColor("#8FA47C")),
    )

    private fun loadIcon(ctx: Context, pkg: String): Drawable? =
        try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    /** 안드로이드 13+의 '테마 아이콘'용 단색 레이어 (앱이 제공할 때만) */
    private fun monochromeOf(d: Drawable): Drawable? =
        if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /** 위젯용 앱 아이콘. mono=true면 흰색 단색 아이콘(없으면 원래 아이콘) */
    fun appIcon(ctx: Context, pkg: String, size: Int, mono: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = loadIcon(ctx, pkg)?.mutate() ?: return bmp
        val m = if (mono) monochromeOf(d)?.mutate() else null
        if (m != null) {
            // 어댑티브 아이콘은 108 중 가운데 72만 보이므로 바깥 25%씩 넓혀서 그린다
            val inset = (size * 0.25f).toInt()
            m.setTint(Color.WHITE)
            m.setBounds(-inset, -inset, size + inset, size + inset)
            m.draw(c)
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return bmp
    }

    /** 파스텔 바로가기 아이콘 (어댑티브 비트맵: 108dp 기준 전체 캔버스) */
    fun pastelIcon(ctx: Context, pkg: String, pastel: Pastel): Bitmap {
        val size = 432
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(pastel.bg)
        val d = loadIcon(ctx, pkg)?.mutate() ?: return bmp
        val m = monochromeOf(d)?.mutate()
        if (m != null) {
            m.setTint(pastel.glyph)
            m.setBounds(0, 0, size, size) // 같은 108 좌표계라 그대로 겹치면 됨
            m.draw(c)
        } else {
            // 단색 레이어가 없는 앱: 원래 아이콘을 작게 가운데에
            val s = (size * 0.36f).toInt()
            val o = (size - s) / 2
            d.setBounds(o, o, o + s, o + s)
            d.draw(c)
        }
        return bmp
    }

    /** 배터리 링 */
    fun batteryRing(size: Int, percent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.09f
        val r = RectF(stroke, stroke, size - stroke, size - stroke)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        p.color = 0x40FFFFFF
        c.drawArc(r, 0f, 360f, false, p)
        p.color = Color.WHITE
        c.drawArc(r, -90f, 360f * percent / 100f, false, p)
        return bmp
    }
}
