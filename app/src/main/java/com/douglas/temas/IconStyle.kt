package com.douglas.temas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Capa de personalización de íconos estilo Apple: todos con esquinas redondeadas y un estilo
 * (Original / Cristal / Color / Claro / Oscuro / Teñido) global o por app, o una imagen propia por app.
 * Los íconos se dibujan en alta resolución (192 px) y se guardan en caché, así se ven nítidos.
 */
object IconStyle {
    const val ORIGINAL = 0
    const val GLASS = 1
    const val COLOR = 2
    const val LIGHT = 3
    const val DARK = 4
    const val TINT = 5
    const val CYBER = 6
    private val names = arrayOf("Original", "Cristal", "Color", "Claro", "Oscuro", "Teñido", "Cyberpunk")
    val styleNames: Array<String> get() = names

    val tintNames = arrayOf("Azul", "Rosa", "Verde", "Naranja", "Morado", "Rojo", "Turquesa")
    private val tintColors = longArrayOf(0xFF4F8CFF, 0xFFFF5C8A, 0xFF34C759, 0xFFFF9F0A, 0xFFAF52DE, 0xFFFF453A, 0xFF30D5C8)
        .map { it.toInt() }.toIntArray()

    private const val SIZE = 192
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // ---- Estilo global y por app ----
    fun mode(ctx: Context): Int = prefs(ctx).getInt("icon_style", CYBER).coerceIn(0, names.size - 1)
    fun modeName(ctx: Context): String = names[mode(ctx)]
    fun cycle(ctx: Context) { prefs(ctx).edit().putInt("icon_style", (mode(ctx) + 1) % names.size).apply() }

    /** -1 = esta app sigue el estilo global. */
    fun appMode(ctx: Context, pkg: String): Int {
        val v = prefs(ctx).getInt("icon_style_$pkg", -1)
        return if (v in 0 until names.size) v else -1
    }
    fun setAppMode(ctx: Context, pkg: String, m: Int) {
        val e = prefs(ctx).edit()
        if (m < 0) e.remove("icon_style_$pkg") else e.putInt("icon_style_$pkg", m)
        e.apply()
    }
    private fun modeFor(ctx: Context, pkg: String, force: Int = -1): Int =
        if (force >= 0) force else appMode(ctx, pkg).let { if (it >= 0) it else mode(ctx) }

    // ---- Tinte (para el estilo "Teñido") ----
    fun tintIndex(ctx: Context): Int = prefs(ctx).getInt("icon_tint", 0).coerceIn(0, tintColors.size - 1)
    fun setTint(ctx: Context, i: Int) { prefs(ctx).edit().putInt("icon_tint", i).apply() }
    fun tintName(ctx: Context): String = tintNames[tintIndex(ctx)]

    // ---- Imagen propia ----
    fun customFile(ctx: Context, pkg: String): File {
        val dir = File(ctx.filesDir, "icons")
        dir.mkdirs()
        return File(dir, pkg.replace('/', '_') + ".png")
    }
    fun hasCustom(ctx: Context, pkg: String): Boolean = customFile(ctx, pkg).exists()

    /** Ícono de la app en alta densidad (si la radio tiene pantalla de baja densidad, el normal sale borroso). */
    private fun hiRes(ctx: Context, pkg: String, fallback: Drawable?): Drawable? {
        try {
            val pm = ctx.packageManager
            val comp = pm.getLaunchIntentForPackage(pkg)?.component
            val res = if (comp != null) pm.getActivityInfo(comp, 0).let { if (it.icon != 0) it.icon else it.applicationInfo.icon }
                      else pm.getApplicationInfo(pkg, 0).icon
            if (res != 0) {
                val d = pm.getResourcesForApplication(pkg).getDrawableForDensity(res, DisplayMetrics.DENSITY_XXHIGH, null)
                if (d != null) return d
            }
        } catch (e: Exception) { }
        if (fallback != null) return fallback
        return try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    /** Forma de esquinas redondeadas tipo Apple (superelipse, más redonda que antes). */
    private fun squircle(size: Float, inset: Float = 0f): Path {
        val r = size / 2f - inset
        val c = size / 2f
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val ct = cos(t); val st = sin(t)
            val x = c + r * sign(ct).toFloat() * abs(ct).pow(0.5).toFloat()
            val y = c + r * sign(st).toFloat() * abs(st).pow(0.5).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun drawTo(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** ¿El ícono llena todo su cuadro con esquinas opacas (ícono cuadrado)? */
    private fun isFullBleed(b: Bitmap): Boolean {
        val e = 3
        val n = b.width - 1 - e
        return listOf(b.getPixel(e, e), b.getPixel(n, e), b.getPixel(e, n), b.getPixel(n, n))
            .all { Color.alpha(it) > 200 }
    }

    private fun avg(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = s.getPixel(x, y)
            if (Color.alpha(p) > 128) { r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++ }
        }
        if (n == 0L) return 0xFF37474F.toInt()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun finish(c: Canvas, f: Float) {
        // recorta con la forma redondeada (bordes suaves) y dibuja un borde fino
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        c.drawPath(squircle(f), mask)
        c.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x26FFFFFF }
        c.drawPath(squircle(f, 1.5f), stroke)
    }

    private fun renderCustom(file: File): Bitmap? {
        val src = BitmapFactory.decodeFile(file.path) ?: return null
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val f = SIZE.toFloat()
        c.saveLayer(0f, 0f, f, f, null)
        c.drawBitmap(src, null, android.graphics.RectF(0f, 0f, f, f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        finish(c, f)
        return out
    }

    private fun render(d: Drawable, m: Int, tint: Int): Bitmap {
        val f = SIZE.toFloat()
        val full = drawTo(d, SIZE)
        if (m == ORIGINAL) return full

        val gp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (m == DARK) {
            gp.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                .72f, 0f, 0f, 0f, 0f,  0f, .72f, 0f, 0f, 0f,  0f, 0f, .72f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)))
        } else if (m == TINT) {
            val k = 1.25f
            val tr = Color.red(tint) / 255f; val tg = Color.green(tint) / 255f; val tb = Color.blue(tint) / 255f
            gp.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                tr * .299f * k, tr * .587f * k, tr * .114f * k, 0f, 0f,
                tg * .299f * k, tg * .587f * k, tg * .114f * k, 0f, 0f,
                tb * .299f * k, tb * .587f * k, tb * .114f * k, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
        }

        // Ícono cuadrado que llena todo su cuadro: se recorta redondeado y ocupa todo
        if (isFullBleed(full)) {
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.saveLayer(0f, 0f, f, f, null)
            c.drawBitmap(full, 0f, 0f, gp)
            full.recycle()
            finish(c, f)
            return out
        }

        // Ícono con forma propia: va dentro de un recuadro con el estilo elegido
        val pad = (f * 0.14f).toInt()
        val glyph = drawTo(d, SIZE - 2 * pad)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.saveLayer(0f, 0f, f, f, null)

        val (c1, c2) = when (m) {
            GLASS -> 0x38FFFFFF to 0x14FFFFFF
            COLOR -> avg(glyph).let { mix(it, Color.BLACK, 0.35f) to mix(it, Color.BLACK, 0.7f) }
            LIGHT -> 0xFFFFFFFF.toInt() to 0xFFD6D9E0.toInt()
            DARK -> 0xFF2B2E36.toInt() to 0xFF0E1015.toInt()
            else -> mix(tint, Color.BLACK, 0.72f) to mix(tint, Color.BLACK, 0.9f)
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, f, f, c1, c2, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, f, f, bg)
        c.drawBitmap(glyph, pad.toFloat(), pad.toFloat(), gp)
        glyph.recycle()
        full.recycle()
        finish(c, f)
        return out
    }


    // ================= Estilo CYBERPUNK =================
    private const val CSIZE = 160

    /** Duotono neón: la luminancia del ícono original se reparte entre un tono casi negro y el neón de la paleta. */
    private fun duotone(pal: Cyber.Pal): ColorFilter {
        val lo = Cyber.mix(pal.neon, Color.BLACK, 0.86f)
        val hi = Cyber.mix(pal.amber, Color.WHITE, 0.18f)
        val k = 1.18f / 255f
        fun row(l: Int, h: Int): FloatArray {
            val a = (h - l) * k
            return floatArrayOf(a * .299f, a * .587f, a * .114f)
        }
        val r = row(Color.red(lo), Color.red(hi)); val g = row(Color.green(lo), Color.green(hi)); val b = row(Color.blue(lo), Color.blue(hi))
        return ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            r[0], r[1], r[2], 0f, Color.red(lo).toFloat(),
            g[0], g[1], g[2], 0f, Color.green(lo).toFloat(),
            b[0], b[1], b[2], 0f, Color.blue(lo).toFloat(),
            0f, 0f, 0f, 1f, 0f)))
    }

    private fun cutPath(l: Float, t: Float, r: Float, b: Float, k: Float) = Cyber.chamfer(l, t, r, b, k)

    /** Dibuja el ícono de [src] (con o sin forma propia) como placa cyberpunk: fondo oscuro, glifo neón, líneas de escaneo y borde con brillo. */
    private fun cyberPlate(ctx: Context, glyphSrc: Bitmap, fullBleed: Boolean): Bitmap {
        val pal = Cyber.pal(ctx)
        val f = CSIZE.toFloat()
        val m = f * 0.04f                 // margen para que quepa el brillo
        val k = f * 0.17f
        val out = Bitmap.createBitmap(CSIZE, CSIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val plate = cutPath(m, m, f - m, f - m, k)

        // fondo
        c.drawPath(plate, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF140808.toInt() })

        // glifo en duotono neón, recortado a la placa
        val inset = if (fullBleed) f * 0.10f else f * 0.20f
        val gp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = duotone(pal) }
        val layer = c.saveLayer(0f, 0f, f, f, null)
        val dst = android.graphics.RectF(inset, inset, f - inset, f - inset)
        // resplandor suave detrás del glifo
        val glowMask = glyphSrc.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(f * 0.04f, BlurMaskFilter.Blur.NORMAL) }, IntArray(2))
        c.drawBitmap(glowMask, null, dst, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cyber.alpha(pal.neon, 0.55f) })
        glowMask.recycle()
        c.drawBitmap(glyphSrc, null, dst, gp)
        if (fullBleed) {
            // un ícono cuadrado llena la placa: se recorta con sus esquinas cortadas
            val inner = cutPath(inset, inset, f - inset, f - inset, k * 0.8f)
            c.drawPath(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        }
        c.restoreToCount(layer)

        // líneas de escaneo
        val sc = Paint().apply { color = 0x57000000; strokeWidth = f * 0.012f }
        var y = f * 0.26f
        while (y < f * 0.94f) { val x2 = if (y > f * 0.79f) f * (0.93f - (y / f - 0.79f)) else f * 0.93f; c.drawLine(f * 0.07f, y, x2, y, sc); y += f * 0.07f }

        // borde con brillo
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = f * 0.05f; color = Cyber.alpha(pal.neon, 0.55f)
            maskFilter = BlurMaskFilter(f * 0.035f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawPath(plate, glow)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = f * 0.026f; strokeJoin = Paint.Join.ROUND; color = pal.neon
        }
        c.drawPath(plate, edge)
        // marcas de esquina (arriba-derecha y abajo-izquierda)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = f * 0.024f; color = pal.amber; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val tp = android.graphics.Path().apply {
            moveTo(f * .82f, f * .11f); lineTo(f * .90f, f * .11f); lineTo(f * .90f, f * .19f)
            moveTo(f * .18f, f * .89f); lineTo(f * .10f, f * .89f); lineTo(f * .10f, f * .81f)
        }
        c.drawPath(tp, tick)
        return out
    }

    private fun renderCyber(ctx: Context, d: Drawable): Bitmap {
        val full = drawTo(d, CSIZE)
        val fb = isFullBleed(full)
        val r = cyberPlate(ctx, full, fb)
        full.recycle()
        return r
    }

    private fun renderCyberCustom(ctx: Context, file: File): Bitmap? {
        val src = BitmapFactory.decodeFile(file.path) ?: return null
        val r = cyberPlate(ctx, src, true)
        src.recycle()
        return r
    }

    /** Vista del ícono ya con el estilo aplicado. El tamaño final lo pone quien la agrega (LayoutParams). */
    fun view(ctx: Context, pkg: String, sizeDp: Int, fallback: Drawable? = null, forceMode: Int = -1): View {
        val custom = customFile(ctx, pkg)
        val m = modeFor(ctx, pkg, forceMode)
        val tint = tintColors[tintIndex(ctx)]
        val hue = if (m == CYBER) Cyber.hue(ctx) else 0
        val key = if (custom.exists()) "c|$pkg|$m|$hue|${custom.lastModified()}" else "$pkg|$m|${if (m == TINT) tint else 0}|$hue"
        var bmp: Bitmap? = cache.get(key)
        if (bmp == null) {
            bmp = try {
                if (custom.exists()) { if (m == CYBER) renderCyberCustom(ctx, custom) else renderCustom(custom) }
                else hiRes(ctx, pkg, fallback)?.let { if (m == CYBER) renderCyber(ctx, it) else render(it, m, tint) }
            } catch (e: Exception) { null }
            if (bmp != null) cache.put(key, bmp)
        }
        return ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (bmp != null) setImageBitmap(bmp)
        }
    }
}
