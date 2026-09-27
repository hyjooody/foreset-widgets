package com.forestwidgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/** 모든 앱 아이콘을 숲 테마(파스텔 / 흰색 단색)로 다시 그리는 곳 */
object IconFactory {

    private val cache = HashMap<String, Bitmap>()

    fun pastelFor(pkg: String): IconUtil.Pastel =
        IconUtil.PASTELS[(pkg.hashCode() and 0x7fffffff) % IconUtil.PASTELS.size]

    @Synchronized
    fun clear() = cache.clear()

    /** 파스텔 둥근 사각형 + 아이콘 모양 */
    @Synchronized
    fun pastel(ctx: Context, pkg: String, size: Int): Bitmap = cache.getOrPut("p|$pkg|$size") {
        val pastel = pastelFor(pkg)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pastel.bg }
        val rad = size * 0.3f
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), rad, rad, p)
        val g = glyph(ctx, pkg, size, pastel.glyph)
        if (g != null) {
            c.drawBitmap(g, 0f, 0f, null)
        } else {
            drawOriginal(ctx, pkg, c, size, 0.62f)
        }
        bmp
    }

    /** 흰색 단색 아이콘 (원형 바용). 모양을 못 뽑으면 원래 아이콘 */
    @Synchronized
    fun mono(ctx: Context, pkg: String, size: Int): Bitmap = cache.getOrPut("m|$pkg|$size") {
        glyph(ctx, pkg, size, 0xFFFFFFFF.toInt()) ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawOriginal(ctx, pkg, Canvas(it), size, 0.8f)
        }
    }

    private fun loadIcon(ctx: Context, pkg: String): Drawable? =
        try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun drawOriginal(ctx: Context, pkg: String, c: Canvas, size: Int, scale: Float) {
        val d = loadIcon(ctx, pkg) ?: return
        val s = (size * scale).toInt()
        val o = (size - s) / 2
        d.setBounds(o, o, o + s, o + s)
        d.draw(c)
    }

    /**
     * 앱 아이콘에서 '모양'만 뽑아 한 가지 색으로 칠한다.
     * 1) 안드로이드 13+ 테마 아이콘(단색 레이어)  2) 어댑티브 아이콘의 앞 레이어가 로고 모양일 때
     */
    private fun glyph(ctx: Context, pkg: String, size: Int, color: Int): Bitmap? {
        val d = loadIcon(ctx, pkg) as? AdaptiveIconDrawable ?: return null
        val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
        val layer = mono ?: d.foreground?.takeIf { isSparse(it) } ?: return null

        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = (size * 0.25f).toInt()
        layer.setBounds(-inset, -inset, size + inset, size + inset)
        layer.draw(Canvas(raw))

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        // 살짝 줄여서 여백을 준다
        val m = size * 0.06f
        Canvas(out).drawBitmap(raw, null, RectF(m, m, size - m, size - m), p)
        raw.recycle()
        return out
    }

    /** 앞 레이어가 화면을 거의 꽉 채우면(사진형 아이콘) 모양 추출에 쓰지 않는다 */
    private fun isSparse(fg: Drawable): Boolean {
        val n = 72
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        fg.setBounds(-18, -18, n + 18, n + 18)
        fg.draw(Canvas(bmp))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        val opaque = px.count { (it ushr 24) > 128 }
        val ratio = opaque.toFloat() / px.size
        return ratio in 0.03f..0.55f
    }
}
