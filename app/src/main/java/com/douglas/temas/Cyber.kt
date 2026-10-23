package com.douglas.temas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paleta y utilidades del estilo cyberpunk. Un solo tono (hue) define todo el neón, igual que el HUD del diseño web. */
object Cyber {
    const val DEFAULT_HUE = 18
    val swatches = listOf("Naranja" to 18, "Ámbar" to 42, "Rojo" to 355, "Magenta" to 320, "Cian" to 185, "Verde" to 140, "Azul" to 215)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun hue(ctx: Context): Int = prefs(ctx).getInt("cyber_hue", DEFAULT_HUE).coerceIn(0, 360)
    fun setHue(ctx: Context, h: Int) { prefs(ctx).edit().putInt("cyber_hue", h.coerceIn(0, 360)).apply() }

    /** HSL -> color. Misma fórmula que usa el diseño web. */
    fun hsl(h: Float, s: Float, l: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s / 100f
        val ll = l / 100f
        val a = ss * min(ll, 1f - ll)
        fun f(n: Int): Int {
            val k = (n + hh / 30f) % 12f
            val v = ll - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
            return (255f * v).roundToInt().coerceIn(0, 255)
        }
        return Color.rgb(f(0), f(8), f(4))
    }

    class Pal(val hue: Int) {
        val neon = hsl(hue.toFloat(), 100f, 54f)
        val hot = hsl(hue - 14f, 100f, 50f)
        val amber = hsl(hue + 26f, 100f, 56f)
        val rim = hsl(hue + 6f, 100f, 58f)
        val ember = hsl(hue - 8f, 80f, 24f)
        val text = hsl(hue + 8f, 100f, 89f)
        val dim = hsl(hue.toFloat(), 38f, 58f)
        val panelTop = 0xFF1A0B0B.toInt()
        val panelBottom = 0xFF0C0607.toInt()
        val ink = 0xFF050203.toInt()
        val shift: Float get() = (hue - 18).toFloat()
    }


    // ---- Intro de arranque: solo una vez por encendido de la radio ----
    /** Momento (uptimeMillis) en que empezó la intro; 0 = no hay intro. */
    var introT0 = 0L
    private var introDecided = false

    /** Devuelve el inicio de la intro si debe verse (también si se reconstruye la pantalla a mitad), o 0. */
    fun introStart(ctx: Context): Long {
        val now = SystemClock.uptimeMillis()
        if (!introDecided) {
            introDecided = true
            val p = prefs(ctx)
            val bc = android.provider.Settings.Global.getInt(ctx.contentResolver, "boot_count", -1)
            val bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val isNew = if (bc >= 0) bc != p.getInt("intro_bc", -2) else Math.abs(bootAt - p.getLong("intro_bt", 0L)) > 120000L
            val nowMs = System.currentTimeMillis()
            val stale = nowMs - p.getLong("intro_last", 0L) > 30 * 60 * 1000L
            if (isNew || stale) {
                p.edit().putInt("intro_bc", bc).putLong("intro_bt", bootAt).putLong("intro_last", nowMs).apply()
                introT0 = now
            }
        }
        return if (introT0 != 0L && now - introT0 < CyberIntroView.TOTAL_MS) introT0 else 0L
    }

    /** Un toque salta directo a la entrada de la camioneta. */
    fun introSkip() {
        val now = SystemClock.uptimeMillis()
        if (introT0 != 0L && now - introT0 < CyberIntroView.SLIDE_MS) introT0 = now - CyberIntroView.SLIDE_MS
    }

    private var cached: Pal? = null
    fun pal(ctx: Context): Pal {
        val h = hue(ctx)
        val c = cached
        if (c != null && c.hue == h) return c
        return Pal(h).also { cached = it }
    }

    fun alpha(c: Int, a: Float): Int = Color.argb((a * 255f).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    /** Rectángulo con las esquinas superior-izquierda e inferior-derecha cortadas (el "cut" del diseño). */
    fun chamfer(l: Float, t: Float, r: Float, b: Float, k: Float, p: Path = Path()): Path {
        p.reset()
        p.moveTo(l + k, t); p.lineTo(r, t); p.lineTo(r, b - k)
        p.lineTo(r - k, b); p.lineTo(l, b); p.lineTo(l, t + k)
        p.close()
        return p
    }

    /** Degradado lineal con el mismo ángulo y largo que linear-gradient(<deg>) de CSS (0° = hacia arriba, 90° = hacia la derecha). */
    fun cssGrad(l: Float, t: Float, r: Float, b: Float, deg: Float, colors: IntArray, pos: FloatArray? = null): LinearGradient {
        val a = Math.toRadians(deg.toDouble())
        val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
        val len = abs((r - l) * dx) + abs((b - t) * dy)
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        return LinearGradient(cx - dx * len / 2f, cy - dy * len / 2f, cx + dx * len / 2f, cy + dy * len / 2f, colors, pos, Shader.TileMode.CLAMP)
    }

    /** Texto monoespaciado con tamaño en píxeles (para medidas en cq) y resplandor opcional (radio en px). */
    fun textPx(ctx: Context, s: String, px: Float, color: Int, bold: Boolean = false, glow: Float = 0f, glowColor: Int = color): TextView = TextView(ctx).apply {
        text = s; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px); setTextColor(color)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (glow > 0f) setShadowLayer(glow, 0f, 0f, glowColor)
        includeFontPadding = false
    }

    /** Texto monoespaciado con resplandor. */
    fun text(ctx: Context, s: String, sp: Float, color: Int, bold: Boolean = false, glow: Boolean = true): TextView = TextView(ctx).apply {
        text = s; textSize = sp; setTextColor(color)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        if (glow) setShadowLayer(ctx.dp(5).toFloat(), 0f, 0f, alpha(color, .75f))
        includeFontPadding = false
    }

    /** Matriz de rotación de tono (equivale a hue-rotate de CSS). */
    fun hueMatrix(deg: Float): android.graphics.ColorMatrix {
        val r = Math.toRadians(deg.toDouble())
        val c = Math.cos(r).toFloat(); val s = Math.sin(r).toFloat()
        return android.graphics.ColorMatrix(floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f))
    }
}

/** Panel cyberpunk: borde de degradado neón con esquinas cortadas y relleno oscuro. Es un Drawable simple (sin capas), así que es barato de pintar. */
class CutDrawable(private val pal: Cyber.Pal, private val k: Float, private val border: Float, private val fill: Boolean = true) : Drawable() {
    private val outer = Path()
    private val inner = Path()
    private val pb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pf = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(b: Rect) {
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val bt = b.bottom.toFloat()
        Cyber.chamfer(l, t, r, bt, k, outer)
        Cyber.chamfer(l + border, t + border, r - border, bt - border, max(0f, k - border), inner)
        pb.shader = Cyber.cssGrad(l, t, r, bt, 135f, intArrayOf(pal.neon, pal.ember, pal.neon), floatArrayOf(0f, .5f, 1f))
        pf.shader = Cyber.cssGrad(l + border, t + border, r - border, bt - border, 160f, intArrayOf(pal.panelTop, pal.panelBottom))
    }

    override fun draw(c: Canvas) {
        c.drawPath(outer, pb)
        if (fill) c.drawPath(inner, pf)
    }

    override fun setAlpha(a: Int) { pb.alpha = a; pf.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { pb.colorFilter = cf; pf.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Glifos de línea dibujados con Canvas (no dependen de la fuente de la radio). */
class CyberGlyph(ctx: Context, var kind: Kind, private val color: Int, private val dark: Int = 0xFF1A0603.toInt()) : View(ctx) {
    enum class Kind { GRID, PREV, NEXT, PLAY, PAUSE, PLUS }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rf = RectF()

    fun set(k: Kind) { if (k != kind) { kind = k; invalidate() } }

    private fun poly(c: Canvas, vararg xy: Float) {
        path.reset(); path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        path.close()
        // resplandor + relleno
        c.drawPath(path, fillP)
    }

    override fun onDraw(c: Canvas) {
        val u = min(width, height) / 24f
        if (u <= 0f) return
        c.save()
        c.translate((width - 24f * u) / 2f, (height - 24f * u) / 2f)
        c.scale(u, u)
        fillP.color = color
        line.color = color; line.strokeWidth = 1.8f
        when (kind) {
            Kind.GRID -> {
                for ((x, y) in listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f)) {
                    rf.set(x, y, x + 7f, y + 7f)
                    line.color = Cyber.alpha(color, .3f); line.strokeWidth = 3.4f; c.drawRoundRect(rf, 1.5f, 1.5f, line)
                    line.color = color; line.strokeWidth = 1.8f; c.drawRoundRect(rf, 1.5f, 1.5f, line)
                }
            }
            Kind.PLAY -> poly(c, 8f, 4f, 8f, 20f, 20f, 12f)
            Kind.PAUSE -> { poly(c, 6f, 4f, 10f, 4f, 10f, 20f, 6f, 20f); poly(c, 14f, 4f, 18f, 4f, 18f, 20f, 14f, 20f) }
            Kind.PREV -> { poly(c, 6f, 5f, 8f, 5f, 8f, 19f, 6f, 19f); poly(c, 20f, 5f, 20f, 19f, 9f, 12f) }
            Kind.NEXT -> { poly(c, 16f, 5f, 18f, 5f, 18f, 19f, 16f, 19f); poly(c, 4f, 5f, 4f, 19f, 15f, 12f) }
            Kind.PLUS -> {
                line.color = Cyber.alpha(color, .3f); line.strokeWidth = 3.6f
                c.drawLine(12f, 6f, 12f, 18f, line); c.drawLine(6f, 12f, 18f, 12f, line)
                line.color = color; line.strokeWidth = 1.8f
                c.drawLine(12f, 6f, 12f, 18f, line); c.drawLine(6f, 12f, 18f, 12f, line)
            }
        }
        c.restore()
    }
}


/** Fondo de pantalla del diseño: degradado vertical + resplandor elíptico (70% x 60% centrado en 62% / 100%). */
class CyberBackdrop(private val pal: Cyber.Pal) : Drawable() {
    private val base = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onBoundsChange(b: Rect) {
        base.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), 0xFF0B0405.toInt(), pal.ink, Shader.TileMode.CLAMP)
        glow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(Cyber.alpha(pal.hot, .2f), Cyber.alpha(pal.hot, 0f)), floatArrayOf(0f, .7f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, base)
        c.save()
        c.translate(b.left + b.width() * .62f, b.bottom.toFloat())
        c.scale(b.width() * .7f, b.height() * .6f)
        c.drawCircle(0f, 0f, 1f, glow)
        c.restore()
    }

    override fun setAlpha(a: Int) { base.alpha = a; glow.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { base.colorFilter = cf; glow.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
