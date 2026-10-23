package com.douglas.temas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Intro de arranque (del diseño "Grand Vitara A"): consola que escribe, destello, rinoceronte que corre y deja ver
 * GRAND VITARA, glitch, y la camioneta entra desde la izquierda. Se dibuja encima de la escena de la camioneta.
 * Solo corre en el arranque (ver Cyber.introStart); después queda el ciclo de la camioneta cruzando la roca.
 */
class CyberIntroView(ctx: Context, private val pal: Cyber.Pal) : View(ctx) {
    companion object {
        const val PRE_MS = 1100L        // como en el diseño: primero se ve la escena normal y luego "enciende" la intro
        const val SLIDE_MS = PRE_MS + 9200L   // aquí empieza el desvanecido y la entrada de la camioneta
        const val TOTAL_MS = PRE_MS + 10300L
        private const val IN_SLIDE = 9200L
        private const val IN_TOTAL = 10300L
        private const val CROSS_MS = 2800L
        private const val TRUCK_START = -1.35f
    }

    var onDone: (() -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val lines = listOf("CORVO.NIC/" to 45, "INFOTAINMENT SYSTEM V1.1" to 25, "" to 0, "BOOT CHECK..." to 60, " ADS OK" to 30, " MPU OK" to 30, " RTC OK" to 30)
    private val lineStart = LongArray(lines.size)
    private val tFlash2: Long
    private val tClear: Long
    private val tCross: Long
    private val tGlitch: Long

    private val clip = Path()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    // Fuente del diseño (Racing Sans One): si copias racing_sans_one.ttf a app/src/main/assets/ se usa; si no, una negrita cursiva del sistema.
    private val wordFace: Typeface = try { Typeface.createFromAsset(ctx.assets, "racing_sans_one.ttf") } catch (e: Exception) { Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC) }
    private val bootP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = pal.amber }
    private val pg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = wordFace; textAlign = Paint.Align.CENTER }
    private val pv = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = wordFace; textAlign = Paint.Align.CENTER }
    private val py = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = pal.dim }
    private val gridP = Paint().apply { color = 0x66000000; strokeWidth = 1f }
    private var grid = FloatArray(0)

    // geometría del bloque de texto
    private var lockLeft = 0f
    private var lockW = 0f
    private var lockTop = 0f
    private var yH = 0f
    private var gH = 0f
    private var vH = 0f
    private val mount = Array(4) { Path() }
    private val mountA = floatArrayOf(.4f, .28f, .2f, .12f)
    private var removed = false

    // ---- rinoceronte (viewBox 220x130) ----
    private fun poly(vararg v: Float): Path = Path().apply {
        moveTo(v[0], v[1]); var i = 2
        while (i < v.size) { lineTo(v[i], v[i + 1]); i += 2 }
        close()
    }
    private val body = poly(14f, 92f, 10f, 82f, 22f, 68f, 48f, 60f, 68f, 42f, 80f, 34f, 88f, 48f, 128f, 38f, 170f, 44f, 200f, 62f, 206f, 92f, 190f, 100f, 140f, 98f, 100f, 100f, 60f, 98f, 34f, 96f)
    private val tail = poly(206f, 70f, 216f, 84f, 208f, 88f)
    private val earA = poly(68f, 42f, 74f, 26f, 84f, 40f)
    private val earAs = poly(68f, 42f, 74f, 26f, 78f, 38f)
    private val horn = poly(22f, 68f, 8f, 34f, 44f, 60f)
    private val hornS = poly(22f, 68f, 8f, 34f, 25f, 62f)
    private val ear2 = poly(48f, 60f, 44f, 46f, 58f, 52f)
    // sombras y luces: (path, color, alpha)
    private val shade = listOf(
        Triple(poly(10f, 82f, 22f, 68f, 48f, 60f, 56f, 78f, 34f, 96f, 14f, 92f), Color.BLACK, .22f),
        Triple(poly(48f, 60f, 68f, 42f, 88f, 48f, 96f, 80f, 56f, 78f), Color.WHITE, .14f),
        Triple(poly(88f, 48f, 128f, 38f, 120f, 70f, 96f, 80f), Color.BLACK, .18f),
        Triple(poly(128f, 38f, 170f, 44f, 150f, 64f, 120f, 70f), Color.WHITE, .10f),
        Triple(poly(170f, 44f, 200f, 62f, 206f, 92f, 190f, 100f, 160f, 80f, 150f, 64f), Color.BLACK, .25f),
        Triple(poly(56f, 78f, 96f, 80f, 120f, 70f, 150f, 64f, 160f, 80f, 140f, 98f, 100f, 100f, 60f, 98f, 34f, 96f), Color.BLACK, .15f))
    // patas: (origen x, delay ms, pata, pie, opacidad)
    private class Leg(val ox: Float, val delay: Float, val leg: Path, val foot: Path, val a: Float)
    private val far = listOf(
        Leg(72f, 250f, poly(62f, 92f, 82f, 92f, 80f, 124f, 66f, 124f), poly(66f, 118f, 80f, 118f, 80f, 124f, 66f, 124f), .55f),
        Leg(152f, 0f, poly(142f, 92f, 162f, 92f, 160f, 124f, 146f, 124f), poly(146f, 118f, 160f, 118f, 160f, 124f, 146f, 124f), .55f))
    private val near = listOf(
        Leg(42f, 0f, poly(30f, 92f, 54f, 92f, 51f, 124f, 35f, 124f), poly(35f, 118f, 51f, 118f, 51f, 124f, 35f, 124f), 1f),
        Leg(178f, 250f, poly(166f, 92f, 190f, 92f, 186f, 124f, 171f, 124f), poly(171f, 118f, 186f, 118f, 186f, 124f, 171f, 124f), 1f))

    init {
        var t = 250L
        for ((i, l) in lines.withIndex()) {
            lineStart[i] = t
            if (l.first.isEmpty()) continue
            t += l.first.length * l.second + (if (i >= 4) 260 else 140)
        }
        tFlash2 = t + 500
        tClear = tFlash2 + 120
        tCross = tClear + 250
        tGlitch = tCross + CROSS_MS + 1400
        isClickable = true
        setOnClickListener { Cyber.introSkip() }      // un toque salta a la entrada de la camioneta
    }

    private fun elapsed(): Long { val t0 = Cyber.introT0; return if (t0 == 0L) TOTAL_MS else SystemClock.uptimeMillis() - t0 }

    /** Desplazamiento horizontal de la camioneta (fracción de su ancho): fuera de pantalla durante la intro y entra al final. */
    fun truckOffset(): Float {
        val e = elapsed()
        return when {
            e < PRE_MS -> 0f
            e < SLIDE_MS -> TRUCK_START
            e < SLIDE_MS + 1000 -> TRUCK_START * (1f - (e - SLIDE_MS) / 1000f).pow(4)
            else -> 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        val fw = w.toFloat(); val fh = h.toFloat(); val cw = fw / 100f
        val cqs = max(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) / 100f
        Cyber.chamfer(0f, 0f, fw, fh, 1.8f * cqs, clip)
        pg.textSize = 9f * cw; pg.letterSpacing = .4f / 9f
        pv.textSize = 19f * cw; pv.letterSpacing = .3f / 19f
        py.textSize = 3.2f * cw; py.letterSpacing = .5f / 3.2f
        bootP.textSize = 3.4f * cw
        lockW = max(pv.measureText("VITARA"), pg.measureText("GRAND"))
        yH = 3.2f * cw * .95f; gH = 9f * cw * .95f; vH = 19f * cw * .95f
        val total = yH + .8f * cw + gH + vH
        lockTop = (fh - total) / 2f
        lockLeft = (fw - lockW) / 2f
        // cordillera al fondo (svg 400x120 estirado a todo el ancho, 38 % de alto)
        val pts = floatArrayOf(0f, 100f, 30f, 80f, 55f, 86f, 90f, 50f, 120f, 70f, 160f, 25f, 195f, 60f, 225f, 40f, 262f, 78f, 300f, 55f, 340f, 82f, 370f, 70f, 400f, 96f)
        val mh = fh * .38f
        for (k in 0 until 4) {
            val p = mount[k]; p.reset()
            var i = 0
            while (i < pts.size) {
                val x = pts[i] / 400f * fw
                val y = fh - mh + (pts[i + 1] + 9f * k) / 120f * mh
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                i += 2
            }
        }
        // rejilla de líneas de escaneo (cada 3 dp, horizontales y verticales)
        val step = max(3f, 3f * d)
        val n = (fh / step).toInt() + (fw / step).toInt() + 2
        grid = FloatArray(n * 4)
        var j = 0; var y = 0f
        while (y < fh) { grid[j++] = 0f; grid[j++] = y; grid[j++] = fw; grid[j++] = y; y += step }
        var x = 0f
        while (x < fw) { grid[j++] = x; grid[j++] = 0f; grid[j++] = x; grid[j++] = fh; x += step }
    }

    private fun lerp(t: Float, xs: FloatArray, ys: FloatArray): Float {
        for (i in 1 until xs.size) if (t <= xs[i]) { val f = (t - xs[i - 1]) / (xs[i] - xs[i - 1]); return ys[i - 1] + (ys[i] - ys[i - 1]) * f }
        return ys[ys.size - 1]
    }
    private val oledX = floatArrayOf(0f, .08f, .12f, .20f, .28f, .40f, 1f)
    private val oledY = floatArrayOf(0f, 1f, .1f, 1f, .45f, 1f, 1f)

    private fun baseline(p: Paint, lineTop: Float, lh: Float): Float {
        val fm = p.fontMetrics
        return lineTop + (lh - (fm.descent - fm.ascent)) / 2f - fm.ascent
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val raw = elapsed()
        if (raw >= TOTAL_MS) {
            if (!removed) { removed = true; onDone?.invoke(); post { (parent as? ViewGroup)?.removeView(this) } }
            return
        }
        if (raw < PRE_MS) { postInvalidateOnAnimation(); return }     // todavía no enciende: se ve la escena de abajo
        val e = raw - PRE_MS
        val cw = w / 100f
        c.save(); c.clipPath(clip)
        // desvanecido final en 6 pasos (como steps(6) del diseño)
        var a = 1f
        if (e >= IN_SLIDE) a = 1f - floor(((e - IN_SLIDE) / 500f).coerceIn(0f, 1f) * 6f) / 6f
        val layer = if (a < 1f) c.saveLayerAlpha(0f, 0f, w, h, (a * 255f).toInt()) else -1
        c.drawColor(Color.BLACK)

        drawBoot(c, e, cw)
        drawWord(c, e, w, h, cw)
        if (e >= tCross && e < tCross + CROSS_MS) drawRhino(c, e, w, h, cw)

        // destellos de encendido
        for (s in longArrayOf(0L, tFlash2)) {
            val dd = (e - s).toFloat()
            if (dd in 0f..350f) {
                val p = dd / 350f
                val al = if (p < .3f) .5f * p / .3f else .5f * (1f - p) / .7f
                c.drawColor(Cyber.alpha(pal.amber, al))
            }
        }
        c.drawLines(grid, gridP)
        if (layer >= 0) c.restoreToCount(layer)
        c.restore()
        postInvalidateOnAnimation()
    }

    private fun drawBoot(c: Canvas, e: Long, cw: Float) {
        if (e >= tClear) return
        bootP.alpha = (255f * lerp(min(e / 800f, 1f), oledX, oledY)).toInt().coerceIn(0, 255)
        bootP.setShadowLayer(1.2f * cw, 0f, 0f, Cyber.alpha(pal.amber, .7f))
        val lh = bootP.textSize * 1.3f
        for ((i, l) in lines.withIndex()) {
            if (e < lineStart[i]) break
            if (l.first.isEmpty()) continue
            val n = min(l.first.length, ((e - lineStart[i]) / l.second).toInt() + 1)
            c.drawText(l.first.substring(0, n), 5f * cw, baseline(bootP, 5f * cw + i * lh, lh), bootP)
        }
    }

    private fun drawWord(c: Canvas, e: Long, w: Float, h: Float, cw: Float) {
        if (e < tCross) return
        val revealX = if (e < tCross + CROSS_MS) max(0f, rhinoX(e, w, cw)) else 0f
        var dx = 0f; var dy = 0f; var skew = 0f; var al = 1f
        var m = 0f; var cy = 0f           // desplazamiento de las sombras magenta y cian (en cqw)
        if (e >= tGlitch) {
            val f = (e - tGlitch) / 550f
            when {
                f < .12f -> {}
                f < .24f -> { dx = -1.2f; skew = -14f; m = .8f; cy = -.8f }
                f < .36f -> { dx = 1.4f; dy = .3f; skew = 10f; m = -.8f; cy = .8f }
                f < .50f -> { dx = -.6f; dy = -.4f }
                f < .70f -> { dx = 1f; skew = -8f; m = -.6f; cy = .6f }
                f < 1f -> al = .6f
                else -> return
            }
        }
        c.save()
        c.clipRect(revealX, 0f, w, h)
        c.translate(w / 2f, h / 2f); c.skew(tan(Math.toRadians(skew.toDouble())).toFloat(), 0f); c.translate(-w / 2f, -h / 2f)
        c.translate(dx * cw, dy * cw)
        // cordillera
        strokeP.strokeWidth = 1.2f * d
        for (k in 0 until 4) { strokeP.color = Cyber.alpha(pal.amber, mountA[k] * al); c.drawPath(mount[k], strokeP) }

        val chroma = m != 0f || cy != 0f
        val gTop = lockTop + yH + .8f * cw
        val vTop = gTop + gH
        fun lock(color: Int, off: Float, glow: Boolean, alpha: Float) {
            val ox = off * cw
            for (p in arrayOf(pg, pv)) {
                p.color = Cyber.alpha(color, alpha)
                if (glow) p.setShadowLayer(1.6f * cw, 0f, 0f, Cyber.alpha(pal.amber, .8f * alpha)) else p.clearShadowLayer()
            }
            c.drawText("GRAND", w / 2f + ox, baseline(pg, gTop, gH), pg)
            c.drawText("VITARA", w / 2f + ox, baseline(pv, vTop, vH), pv)
        }
        if (chroma) {
            lock(0xFFFF00FF.toInt(), m, false, al)
            lock(0xFF00FFFF.toInt(), cy, false, al)
        }
        lock(pal.amber, 0f, !chroma, al)
        // años (sin brillo)
        py.color = Cyber.alpha(pal.dim, al)
        py.textAlign = Paint.Align.LEFT
        c.drawText("1998", lockLeft, baseline(py, lockTop, yH), py)
        py.textAlign = Paint.Align.RIGHT
        c.drawText("2006", lockLeft + lockW, baseline(py, lockTop, yH), py)
        c.restore()
    }

    private fun rhinoX(e: Long, w: Float, cw: Float): Float {
        val p = ((e - tCross) / CROSS_MS.toFloat()).coerceIn(0f, 1f)
        return w * 1.1f - p * (w * 1.1f + 36f * cw * .6f)
    }

    private fun fill(c: Canvas, p: Path, color: Int, alpha: Float, stroke: Boolean = true) {
        fillP.color = Cyber.alpha(color, alpha); c.drawPath(p, fillP)
        if (stroke) { strokeP.strokeWidth = 1f; strokeP.color = 0x80000000.toInt(); c.drawPath(p, strokeP) }
    }

    private fun drawLeg(c: Canvas, l: Leg, te: Float) {
        c.save()
        c.rotate(-24f * cos(2f * PI.toFloat() * (te + l.delay) / 500f), l.ox, 92f)
        fill(c, l.leg, pal.amber, l.a)
        fill(c, l.foot, Color.BLACK, .35f)
        c.restore()
    }

    private fun drawRhino(c: Canvas, e: Long, w: Float, h: Float, cw: Float) {
        val te = (e - tCross).toFloat()
        val p = te / CROSS_MS
        val rw = 36f * cw
        val cx = rhinoX(e, w, cw)
        val s = rw / 220f
        c.save()
        c.translate(cx - rw / 2f, h / 2f - .3f * rw + sin(p * 34f) * .7f * cw)
        c.scale(s, s)
        // resplandor
        strokeP.color = Cyber.alpha(pal.amber, .10f); strokeP.strokeWidth = 14f; c.drawPath(body, strokeP)
        strokeP.color = Cyber.alpha(pal.amber, .18f); strokeP.strokeWidth = 7f; c.drawPath(body, strokeP)
        for (l in far) drawLeg(c, l, te)
        fill(c, body, pal.amber, 1f); fill(c, tail, pal.amber, 1f)
        for ((path, col, al) in shade) fill(c, path, col, al)
        fill(c, earA, pal.amber, 1f); fill(c, earAs, Color.WHITE, .25f)
        fill(c, horn, pal.amber, 1f); fill(c, hornS, Color.BLACK, .3f)
        fill(c, ear2, pal.amber, 1f)
        fillP.color = Color.BLACK; c.drawCircle(52f, 65f, 2.2f, fillP)
        for (l in near) drawLeg(c, l, te)
        c.restore()
    }
}
