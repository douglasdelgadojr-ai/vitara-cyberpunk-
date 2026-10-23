package com.douglas.temas

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout.LayoutParams as LP
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tema Cyberpunk (basado en el diseño web BJ30 neón).
 *  - Barra lateral (slots 10-13) y 4 tarjetas (slots 20-23): mantén pulsado un ícono para cambiar la app.
 *  - Panel de la camioneta (slot 30): al tocarlo abre esa app (Waze por defecto); mantenlo pulsado para cambiarla.
 *  - Todos los íconos de apps usan el estilo Cyberpunk, sin importar el estilo global.
 *  - El selector de color del HUD está oculto: toca 5 veces la hora para mostrarlo u ocultarlo.
 */
class CyberpunkTheme : LauncherTheme {
    override val name = "Cyberpunk"

    companion object {
        /** El selector queda abierto aunque el tema se reconstruya al cambiar de color. */
        var pickerOpen = false
    }

    private val TAGLINE = "GRAND VITARA · 1998–2006 · CORVO.NIC/"

    private var apps: List<ResolveInfo> = emptyList()

    /** Apps que se ponen solas la primera vez (por nombre o paquete). Luego tú las cambias. */
    private val defaults: Map<Int, List<String>> = mapOf(
        10 to listOf("medios", "music", "música"),
        11 to listOf("teléfono", "telefono", "dialer"),
        12 to listOf(NAV_PKG),
        13 to listOf("ajustes", "settings", "configuración"),
        20 to listOf("carplay", "rlink"),
        21 to listOf("easyconn"),
        22 to listOf("bluetooth"),
        23 to listOf("foto", "galer", "gallery"),
        30 to listOf(NAV_PKG)
    )

    private fun findApp(keys: List<String>): String? {
        for (k in keys) for (ri in apps) if (ri.activityInfo.packageName.contains(k, true)) return ri.activityInfo.packageName
        return null
    }

    private fun findByLabel(ctx: Context, keys: List<String>): String? {
        val pm = ctx.packageManager
        for (k in keys) for (ri in apps) if (ri.loadLabel(pm).toString().contains(k, true)) return ri.activityInfo.packageName
        return null
    }

    /** null = vacío. "" guardado = el usuario lo quitó a propósito. */
    private fun resolve(ctx: Context, host: ThemeHost, slot: Int): String? {
        val saved = host.dockPackage(slot)
        if (saved != null) return saved.ifEmpty { null }
        val keys = defaults[slot] ?: return null
        return findApp(keys) ?: findByLabel(ctx, keys)
    }

    /** "98.5" grande y "FM" pequeño en neón, como en la vista previa. null si el texto no parece una frecuencia. */
    private fun freqSpan(pal: Cyber.Pal, t: String): CharSequence? {
        val m = Regex("""\d{2,3}[.,]\d{1,2}""").find(t) ?: return null
        val band = if (t.contains("AM", true) && !t.contains("FM", true)) "AM" else "FM"
        val n = m.value.length
        return android.text.SpannableString(m.value.replace(',', '.') + band).apply {
            setSpan(android.text.style.RelativeSizeSpan(.32f), n, length, 0)
            setSpan(android.text.style.ForegroundColorSpan(pal.neon), n, length, 0)
        }
    }

    private fun appLabel(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try { ctx.packageManager.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }

    /** Clima de Managua desde Open-Meteo (sin clave). Si no hay internet se queda en "--°C". */
    private fun fetchWeather(root: View, temp: TextView) {
        Thread {
            var ok = false
            try {
                val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=12.136&longitude=-86.251&current=temperature_2m")
                val c = url.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                val t = Math.round(org.json.JSONObject(txt).getJSONObject("current").getDouble("temperature_2m")).toInt()
                root.post { temp.text = "$t°C" }
                ok = true
            } catch (e: Exception) { }
            root.postDelayed({ if (root.isAttachedToWindow) fetchWeather(root, temp) }, if (ok) 15 * 60 * 1000L else 2 * 60 * 1000L)
        }.start()
    }

    // ---------------------------------------------------------------- slots editables
    // ---- Medidas: todo va en "cq" (1 cq = 1 % del ancho de la pantalla), igual que cqw en la vista previa HTML ----
    private fun cqOf(ctx: Context): Float {
        val m = ctx.resources.displayMetrics
        return max(m.widthPixels, m.heightPixels) / 100f
    }

    private fun slot(ctx: Context, host: ThemeHost, pal: Cyber.Pal, n: Int, iconPx: Int, labeled: Boolean, cq: Float, bd: Float): View {
        val pkg = resolve(ctx, host, n)
        val installed = pkg != null && isInstalled(ctx, pkg)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = CutDrawable(pal, 1.1f * cq, bd)
        }
        if (installed && pkg != null) {
            box.addView(IconStyle.view(ctx, pkg, 0, null, IconStyle.CYBER), LP(iconPx, iconPx))
            if (labeled) box.addView(Cyber.textPx(ctx, appLabel(ctx, pkg), 1.15f * cq, pal.dim).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            }, LP(-1, -2).apply { topMargin = (.3f * cq).toInt() })
        } else {
            box.addView(CyberGlyph(ctx, CyberGlyph.Kind.PLUS, pal.dim).apply {
                background = CutDrawable(pal, .17f * iconPx, max(1f, .026f * iconPx), false)
            }, LP(iconPx, iconPx))
            if (labeled) box.addView(Cyber.textPx(ctx, "Añadir", 1.15f * cq, pal.dim).apply { gravity = Gravity.CENTER },
                LP(-1, -2).apply { topMargin = (.3f * cq).toInt() })
        }
        box.setOnClickListener { if (installed && pkg != null) host.launch(pkg) else host.pickDockSlot(n) }
        box.setOnLongClickListener { host.pickDockSlot(n); true }   // mantener = cambiar la app de este lugar
        return box
    }

    private fun ctlButton(ctx: Context, pal: Cyber.Pal, kind: CyberGlyph.Kind, main: Boolean, sizePx: Int, bd: Float, onClick: () -> Unit): Pair<View, CyberGlyph> {
        val g = CyberGlyph(ctx, kind, if (main) 0xFF1A0603.toInt() else pal.neon)
        val inset = (sizePx * .225f).toInt()      // el glifo ocupa el 55 % del botón
        val b = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (main) pal.neon else Color.TRANSPARENT)
                setStroke(max(1, bd.toInt()), pal.neon)
            }
            addView(g, FrameLayout.LayoutParams(-1, -1).apply { setMargins(inset, inset, inset, inset) })
            setOnClickListener { onClick() }
        }
        return b to g
    }

    // ---------------------------------------------------------------- selector de color (oculto)
    private fun buildPicker(ctx: Context, host: ThemeHost, pal: Cyber.Pal): View {
        val bar = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = CutDrawable(pal, ctx.dp(10).toFloat(), ctx.dp(1).coerceAtLeast(1).toFloat())
            setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        }
        bar.addView(Cyber.text(ctx, "COLOR DEL HUD", 12f, pal.amber, true), LP(-2, -2).apply { rightMargin = ctx.dp(14) })
        for ((nm, h) in Cyber.swatches) {
            val sw = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Cyber.hsl(h.toFloat(), 100f, 54f))
                    setStroke(ctx.dp(2).coerceAtLeast(2), if (pal.hue == h) Color.WHITE else 0x44FFFFFF)
                }
                contentDescription = nm
                setOnClickListener { Cyber.setHue(ctx, h); host.rebuildTheme() }
            }
            bar.addView(sw, LP(ctx.dp(30), ctx.dp(30)).apply { rightMargin = ctx.dp(8) })
        }
        val seek = SeekBar(ctx).apply {
            max = 360; progress = pal.hue
            progressTintList = android.content.res.ColorStateList.valueOf(pal.neon)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { Cyber.setHue(ctx, s?.progress ?: pal.hue); host.rebuildTheme() }
            })
        }
        bar.addView(seek, LP(ctx.dp(240), -2).apply { leftMargin = ctx.dp(6); rightMargin = ctx.dp(10) })
        bar.addView(Cyber.text(ctx, "✕", 18f, pal.text).apply {
            gravity = Gravity.CENTER; setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
            setOnClickListener { pickerOpen = false; bar.visibility = View.GONE }
        })
        bar.visibility = if (pickerOpen) View.VISIBLE else View.GONE
        return bar
    }

    // ---------------------------------------------------------------- pantalla

    override fun build(ctx: Context, host: ThemeHost): View {
        apps = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != ctx.packageName }
        val pal = Cyber.pal(ctx)
        val cq = cqOf(ctx)
        val bd = max(1f, 1.5f * ctx.resources.displayMetrics.density)      // borde de 1.5 px del diseño
        fun q(v: Float) = (v * cq).toInt()

        val frame = FrameLayout(ctx)
        frame.background = CyberBackdrop(pal)

        // .home { inset: 1.3cqw; columnas 7.4cqw | 1fr | 1.62fr; filas 1fr | 9.4cqw; gap 1.1cqw }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(q(1.3f), q(1.3f), q(1.3f), q(1.3f))
        }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(ctx)
        root.addView(top, LP(-1, 0, 1f))

        // ---- Barra lateral: 4 apps editables ----
        val rail = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until 4) rail.addView(slot(ctx, host, pal, 10 + i, q(5.8f), false, cq, bd),
            LP(-1, 0, 1f).apply { if (i > 0) topMargin = q(1.1f) })
        top.addView(rail, LP(q(7.4f), -1))

        // ---- Reproductor ----
        val player = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = CutDrawable(pal, 1.5f * cq, bd)
            setPadding(q(1.4f), q(1.4f), q(1.4f), q(1.4f))
        }
        val gap = q(.6f)
        val source = Cyber.textPx(ctx, "REPRODUCTOR", 1.05f * cq, pal.amber).apply { gravity = Gravity.CENTER; letterSpacing = .19f }
        val title = Cyber.textPx(ctx, "—", 2.1f * cq, pal.text, true, 1.4f * cq, Cyber.alpha(pal.neon, .9f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER }
        val artist = Cyber.textPx(ctx, "", 1.5f * cq, pal.neon).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER }
        val album = Cyber.textPx(ctx, "", 1.1f * cq, pal.dim).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER }
        val viz = CyberVizView(ctx, pal, cq)
        player.addView(source, LP(-1, -2))
        player.addView(title, LP(-1, -2).apply { topMargin = gap })
        player.addView(artist, LP(-1, 0).apply { topMargin = gap })
        player.addView(album, LP(-1, q(1.3f)).apply { topMargin = gap })
        player.addView(viz, LP(-1, 0, 1f).apply { topMargin = gap })

        val ctl = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        fun btnLP(size: Float) = LP(q(size), q(size)).apply { setMargins(q(.6f), 0, q(.6f), 0) }
        ctl.addView(ctlButton(ctx, pal, CyberGlyph.Kind.PREV, false, q(3.6f), bd) { host.media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }.first, btnLP(3.6f))
        val play = ctlButton(ctx, pal, CyberGlyph.Kind.PLAY, true, q(4.4f), bd) { host.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        ctl.addView(play.first, btnLP(4.4f))
        ctl.addView(ctlButton(ctx, pal, CyberGlyph.Kind.NEXT, false, q(3.6f), bd) { host.media(KeyEvent.KEYCODE_MEDIA_NEXT) }.first, btnLP(3.6f))
        player.addView(ctl, LP(-1, -2).apply { topMargin = gap })
        top.addView(player, LP(0, -1, 1f).apply { setMargins(q(1.1f), 0, q(1.1f), 0) })

        // ---- Columna derecha: 4 tarjetas (24 % del alto) + panel de la camioneta ----
        val right = PctColumn(ctx, .24f)
        val cards = LinearLayout(ctx)
        for (i in 0 until 4) cards.addView(slot(ctx, host, pal, 20 + i, q(4.8f), true, cq, bd),
            LP(0, -1, 1f).apply { if (i > 0) leftMargin = q(1f) })
        right.addView(cards, LP(-1, q(10f)))

        val roadBox = FrameLayout(ctx).apply { background = CutDrawable(pal, 1.8f * cq, bd) }
        val road = CyberRoadView(ctx, pal)
        val ins = Math.ceil(bd.toDouble()).toInt()
        roadBox.addView(road, FrameLayout.LayoutParams(-1, -1).apply { setMargins(ins, ins, ins, ins) })
        // intro de arranque (solo al encender la radio); después queda el ciclo de la camioneta
        if (Cyber.introStart(ctx) != 0L) {
            val intro = CyberIntroView(ctx, pal)
            road.intro = intro
            intro.onDone = { road.intro = null }
            roadBox.addView(intro, FrameLayout.LayoutParams(-1, -1).apply { setMargins(ins, ins, ins, ins) })
        }
        roadBox.setOnClickListener {
            val p = resolve(ctx, host, 30)
            if (p != null && isInstalled(ctx, p)) host.launch(p) else host.pickDockSlot(30)
        }
        roadBox.setOnLongClickListener { host.pickDockSlot(30); true }
        right.addView(roadBox, LP(-1, 0, 1f).apply { topMargin = q(1.1f) })
        top.addView(right, LP(0, -1, 1.62f))

        // ---- Barra inferior (9.4cqw de alto, padding 0 2.2cqw, gap 2cqw) ----
        val bar = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = CutDrawable(pal, 1.6f * cq, bd)
            setPadding(q(1.2f), 0, q(2.2f), 0)
        }
        val appsBtn = FrameLayout(ctx).apply {
            addView(CyberGlyph(ctx, CyberGlyph.Kind.GRID, pal.neon), FrameLayout.LayoutParams(q(3f), q(3f), Gravity.CENTER))
            setOnClickListener { host.openDrawer() }
            setOnLongClickListener { host.nextTheme(); true }    // mantener = cambiar de tema
        }
        bar.addView(appsBtn, LP(q(5f), -1))      // 3cq de glifo + 1cq de zona táctil a cada lado
        bar.addView(Cyber.textPx(ctx, TAGLINE, 1.1f * cq, pal.dim).apply {
            maxLines = 1; letterSpacing = .136f
            text = android.text.SpannableString(TAGLINE).apply {
                setSpan(android.text.style.ForegroundColorSpan(pal.amber), 0, 12, 0)
                setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, 12, 0)
            }
        }, LP(-2, -2).apply { leftMargin = q(1f) })
        bar.addView(View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Cyber.alpha(pal.neon, .6f), Cyber.alpha(pal.neon, 0f)))
        }, LP(0, max(2, (2f * ctx.resources.displayMetrics.density).toInt()), 1f).apply { setMargins(q(2f), 0, q(2f), 0) })
        val temp = Cyber.textPx(ctx, "--°C", 2.2f * cq, pal.amber, true, 1f * cq, Cyber.alpha(pal.amber, .6f)).apply { gravity = Gravity.CENTER }
        bar.addView(temp, LP(-2, -2).apply { rightMargin = q(2f) })
        fetchWeather(root, temp)

        val clockBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        fun clock(fmt: String, px: Float, color: Int, bold: Boolean) = TextClock(ctx).apply {
            format12Hour = fmt; format24Hour = fmt; setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px); setTextColor(color); gravity = Gravity.END
            typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
        }
        clockBox.addView(clock("h:mm a", 2.1f * cq, pal.text, true))
        clockBox.addView(clock("EEE d MMM", 1.2f * cq, pal.dim, false))
        // 5 toques seguidos sobre la hora muestran / ocultan el selector de color del HUD
        val picker = buildPicker(ctx, host, pal)
        var taps = 0
        var lastTap = 0L
        clockBox.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            taps = if (now - lastTap < 1500L) taps + 1 else 1
            lastTap = now
            if (taps >= 5) {
                taps = 0
                pickerOpen = !pickerOpen
                picker.visibility = if (pickerOpen) View.VISIBLE else View.GONE
                Toast.makeText(ctx, if (pickerOpen) "Color del HUD: visible" else "Color del HUD: oculto", Toast.LENGTH_SHORT).show()
            }
        }
        bar.addView(clockBox, LP(-2, -2))
        root.addView(bar, LP(-1, q(9.4f)).apply { topMargin = q(1.1f) })
        frame.addView(picker, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = q(12f) })

        // líneas de escaneo sobre todo (una sola capa estática)
        frame.addView(ScanlinesView(ctx), FrameLayout.LayoutParams(-1, -1))

        // ---- Música real: canción, artista, álbum y carátula (Spotify, Bluetooth, radio...) ----
        // artista vacío = 0 de alto; álbum vacío = 1.3cqw (como en el diseño)
        fun fitLine(tv: TextView, emptyH: Int) {
            val lp = tv.layoutParams
            val want = if (tv.text.isNullOrEmpty()) emptyH else -2
            if (lp.height != want) { lp.height = want; tv.layoutParams = lp }
        }
        fun titlePx(v: Float) = title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, v)
        var lastArt: Bitmap? = null
        val tick = object : Runnable {
            override fun run() {
                if (!root.isAttachedToWindow) return
                val np = host.nowPlaying()
                if (np == null) {
                    source.text = "SIN ACCESO"; titlePx(2.1f * cq); title.text = "Dar acceso a la música"
                    artist.text = "Toca aquí y activa «Temas»"; album.text = ""
                    viz.setPlaying(false)
                    if (lastArt != null) { lastArt = null; viz.setArt(null) }
                    player.setOnClickListener { host.requestMediaAccess() }
                } else {
                    val lbl = if (np.pkg.isEmpty()) "" else appLabel(ctx, np.pkg)
                    if (np.source.isNotEmpty()) {
                        source.text = np.source; titlePx(2.1f * cq); title.text = np.title
                        artist.text = np.artist; album.text = np.album
                    } else {
                        val radio = np.pkg.contains("radio", true) || np.pkg.contains("tuner", true) || lbl.contains("radio", true)
                        source.text = if (np.pkg.isEmpty()) "REPRODUCTOR" else if (radio) "RADIO" else lbl.uppercase()
                        val raw = np.title.ifEmpty { if (np.playing) lbl.ifEmpty { "Reproduciendo" } else "Sin reproducción" }
                        val fq = if (radio) freqSpan(pal, raw) else null
                        titlePx(if (fq != null) 5.4f * cq else if (radio) 3f * cq else 2.1f * cq)
                        title.text = fq ?: raw
                        artist.text = np.artist; album.text = np.album
                    }
                    if (np.art !== lastArt) { lastArt = np.art; viz.setArt(np.art) }
                    play.second.set(if (np.playing) CyberGlyph.Kind.PAUSE else CyberGlyph.Kind.PLAY)
                    viz.setPlaying(np.playing)
                    if (np.pkg.isNotEmpty()) player.setOnClickListener { host.launch(np.pkg) }
                    else { player.setOnClickListener(null); player.isClickable = false }
                }
                fitLine(artist, 0); fitLine(album, q(1.3f))
                root.postDelayed(this, 1000)
            }
        }
        root.post(tick)
        return frame
    }
}

/** Columna vertical donde el primer hijo mide un porcentaje fijo del alto (como height:24% del diseño). */
class PctColumn(ctx: Context, private val pct: Float) : LinearLayout(ctx) {
    init { orientation = VERTICAL }
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val h = MeasureSpec.getSize(heightSpec)
        val first = getChildAt(0)
        if (first != null && h > 0) {
            val want = (h * pct).toInt()
            val lp = first.layoutParams
            if (lp.height != want) lp.height = want
        }
        super.onMeasure(widthSpec, heightSpec)
    }
}


/** Líneas de escaneo tipo CRT, dibujadas una sola vez. */
class ScanlinesView(ctx: Context) : View(ctx) {
    private val p = Paint().apply { color = 0x47000000; strokeWidth = 1f }
    init { isClickable = false; isFocusable = false }
    override fun onDraw(c: Canvas) {
        val step = context.dp(3).coerceAtLeast(3).toFloat()
        var y = 0f
        while (y < height) { c.drawLine(0f, y, width.toFloat(), y, p); y += step }
    }
}

/** Anillos giratorios + ecualizador (o carátula del álbum cuando existe). Solo anima mientras suena música. */
class CyberVizView(ctx: Context, private val pal: Cyber.Pal, private val cq: Float) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private var playing = false
    private var art: Bitmap? = null
    private var artShader: BitmapShader? = null
    private var angA = 0f
    private var angB = 0f
    private var tms = 0f
    private var lastNs = 0L
    private var shown = true

    private val ringA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Cyber.alpha(pal.neon, .85f)
        pathEffect = DashPathEffect(floatArrayOf(6f * d, 5f * d), 0f)
    }
    private val ringB = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = pal.hot }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * d; color = Cyber.alpha(pal.neon, .14f) }
    private val barP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val artEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = pal.neon }
    private val oval = RectF()
    private val m = android.graphics.Matrix()
    private val periods = floatArrayOf(1000f, 700f, 1300f, 1000f, 1000f, 1300f, 1000f, 700f, 1300f)
    private val delays = floatArrayOf(0f, 0f, 0f, -400f, -700f, 0f, 0f, -400f, 0f)

    fun setPlaying(on: Boolean) { if (on != playing) { playing = on; lastNs = 0L; invalidate() } }

    fun setArt(b: Bitmap?) {
        art = b
        artShader = if (b != null) BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) else null
        invalidate()
    }

    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); shown = v == VISIBLE; lastNs = 0L; if (shown) invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = min(w, h).toFloat()
        val bh = s * .30f
        barP.shader = LinearGradient(0f, 0f, 0f, -bh, pal.hot, pal.amber, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = min(w, h)
        if (s <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val now = SystemClock.elapsedRealtimeNanos()
        if (playing) {
            if (lastNs != 0L) { val dt = (now - lastNs) / 1e6f; tms += dt; angA = (angA + dt / 18000f * 360f) % 360f; angB = (angB - dt / 7000f * 360f) % 360f }
            lastNs = now
        }

        // anillo exterior (punteado, gira lento)
        c.save(); c.rotate(angA, cx, cy)
        c.drawCircle(cx, cy, s * .44f, glowP)
        c.drawCircle(cx, cy, s * .44f, ringA)
        c.restore()
        // anillo interior (dos arcos, gira al revés)
        oval.set(cx - s * .32f, cy - s * .32f, cx + s * .32f, cy + s * .32f)
        c.save(); c.rotate(angB, cx, cy)
        c.drawArc(oval, -135f, 90f, false, ringB)
        c.drawArc(oval, 45f, 90f, false, ringB)
        c.restore()

        val a = art
        val sh = artShader
        if (a != null && sh != null) {
            val r = s * .27f
            val side = min(a.width, a.height).toFloat()
            m.reset()
            m.postTranslate(-(a.width - side) / 2f, -(a.height - side) / 2f)
            m.postScale(2f * r / side, 2f * r / side)
            m.postTranslate(cx - r, cy - r)
            sh.setLocalMatrix(m)
            artP.shader = sh
            c.drawCircle(cx, cy, r, artP)
            c.drawCircle(cx, cy, r, artEdge)
        } else {
            // ecualizador
            val bw = .7f * cq; val gap = .45f * cq; val bh = s * .30f
            val total = 9 * bw + 8 * gap
            var x = cx - total / 2f
            val base = cy + bh / 2f
            for (i in 0 until 9) {
                val k = if (playing) .18f + .82f * (0.5f - 0.5f * cos((2f * PI.toFloat()) * ((tms + delays[i]) / periods[i]))) else .18f
                c.save(); c.translate(x, base); c.scale(1f, k)
                c.drawRect(0f, -bh, bw, 0f, barP)
                c.restore()
                x += bw + gap
            }
        }
        if (playing && shown) postInvalidateOnAnimation()
    }
}

/** Misma escena del diseño web: camioneta con suspensión pasando sobre una roca, ciudad neón al fondo y chispas. */
class CyberRoadView(ctx: Context, private val pal: Cyber.Pal) : View(ctx) {
    companion object {
        private var truck: Bitmap? = null
        private var wheelF: Bitmap? = null
        private var wheelR: Bitmap? = null
        private var loaded = false
        private const val FRONT = 243f / 1485f
        private const val REAR = 1156f / 1485f
        private const val T = 7000f          // ms que tarda la roca en cruzar
        private const val KS = 60f
        private const val CS = 3.2f
        private const val KT = 1400f
        private const val CT = 55f
        private const val P = 41.93f         // 38 / sin(115°)

        @Synchronized private fun load(ctx: Context) {
            if (loaded) return
            loaded = true
            val am = ctx.applicationContext.assets
            fun get(n: String): Bitmap? = try { am.open(n).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            truck = get("truck.webp"); wheelF = get("wheel_f.webp"); wheelR = get("wheel_r.webp")
        }
        private fun bump(dx: Float, a: Float, h: Float): Float = if (abs(dx) < a) h * (0.5f + 0.5f * cos(PI.toFloat() * dx / a)) else 0f
    }

    private class Rng(var s: Int) {
        fun next(): Float {
            s += 0x6D2B79F5
            var t = (s xor (s ushr 15)) * (1 or s)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return (((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0).toFloat()
        }
    }

    private val d = ctx.resources.displayMetrics.density
    private val cq = max(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels) / 100f
    private val rnd = java.util.Random()
    private var shown = true
    private var t0 = 0L
    private var last = 0L

    // física
    private var wF = 0f; private var uF = 0f; private var wR = 0f; private var uR = 0f
    private var z = 0f; private var vz = 0f; private var pit = 0f; private var vp = 0f
    private var pf = 0f; private var pr = 0f; private var ang = 0f
    private var nextGlitch = 1500f; private var glitchUntil = 0f; private var gA = 0f; private var gB = 0f; private var gDx = 0f
    private var lastV = 0

    // chispas
    private val px = FloatArray(80); private val py = FloatArray(80); private val pvx = FloatArray(80)
    private val pvy = FloatArray(80); private val pl = FloatArray(80); private val pt = FloatArray(80)
    private var pn = 0

    // pinturas y recursos
    private val clip = Path()
    private val bgP = Paint()
    private val glowP = Paint()
    private val fl1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fl2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linP = Paint()
    private val bmpP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val truckP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(Cyber.hueMatrix(pal.shift)) }
    private val ghostP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        val cm = Cyber.hueMatrix(pal.shift - 28f)
        cm.postConcat(ColorMatrix().apply { setSaturation(1.4f) })
        colorFilter = ColorMatrixColorFilter(cm); alpha = 217
    }
    private val beamP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rockFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Cyber.alpha(pal.hot, .12f) }
    private val rockLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = pal.neon }
    private val rockAmb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Cyber.alpha(pal.amber, .8f) }
    private val rockRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Cyber.alpha(pal.rim, .6f) }
    private val sparkP = Paint()
    private val scanP = Paint()
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Cyber.alpha(pal.neon, .9f) }
    private val hudBig = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = pal.amber }
    private val hudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = pal.dim }
    private val barBg = Paint().apply { color = Cyber.alpha(pal.neon, .15f) }
    private val barFg = Paint()
    private val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = pal.rim }
    private val rimF = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = pal.amber }
    private val dstR = RectF()
    private val dashA = DashPathEffect(floatArrayOf(46f, 30f, 14f, 30f), 0f)
    private val dashB = DashPathEffect(floatArrayOf(5f, 8f), 0f)
    private val path = Path()
    private val rockPath = Path()
    private val rr = RectF()

    // destellos horizontales: top, left, ancho (fracciones), retraso y duración (ms)
    private val streaks = arrayOf(
        floatArrayOf(.24f, .06f, .30f, 1000f, 5000f), floatArrayOf(.41f, .52f, .40f, 3200f, 6300f),
        floatArrayOf(.63f, .10f, .22f, 2000f, 4400f), floatArrayOf(.15f, .60f, .26f, 4000f, 7100f))
    private val stP = floatArrayOf(0f, .90f, .92f, .95f, .97f, 1f)
    private val stA = floatArrayOf(0f, 0f, .9f, .25f, .7f, 0f)
    private val stX = floatArrayOf(0f, 0f, .04f, -.002f, -.03f, 0f)
    private val streakP = Paint()

    /** Intro de arranque: mientras corre, la camioneta está fuera de pantalla y luego entra. */
    var intro: CyberIntroView? = null

    private var sky1: Bitmap? = null
    private var sky2: Bitmap? = null
    private var beamTw = -1f
    private var scanH = 0f

    init { load(ctx) }

    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); shown = v == VISIBLE; last = 0L; if (shown) invalidate() }

    private fun sky(seed: Int, alpha: Float, hmin: Float, hmax: Float, col: Int, win: Int, tw: Int, th: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(2, tw), max(2, th), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sx = tw / 900f; val sy = th / 300f
        val r = Rng(seed)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * sy; color = Cyber.alpha(col, alpha) }
        val wp = Paint().apply { color = Cyber.alpha(win, alpha + .15f) }
        var x = 0f
        while (x < 900f) {
            val bw = min(900f - x, 38f + r.next() * 70f)
            val bh = hmin + r.next() * (hmax - hmin)
            val p = Path()
            p.moveTo(x * sx, 300f * sy); p.lineTo(x * sx, (300f - bh) * sy); p.lineTo((x + bw) * sx, (300f - bh) * sy); p.lineTo((x + bw) * sx, 300f * sy)
            c.drawPath(p, line)
            if (r.next() < .35f) { val a = 14f + r.next() * 22f; c.drawLine((x + bw / 2f) * sx, (300f - bh) * sy, (x + bw / 2f) * sx, (300f - bh - a) * sy, line) }
            var wy = 300f - bh + 12f
            while (wy < 290f) {
                var wx = x + 8f
                while (wx < x + bw - 8f) { if (r.next() < .1f) c.drawRect(wx * sx, wy * sy, (wx + 5f) * sx, (wy + 7f) * sy, wp); wx += 14f }
                wy += 16f
            }
            x += bw + (if (r.next() < .5f) 0f else 10f + r.next() * 18f)
        }
        return bmp
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w <= 0 || h <= 0) return
        val fw = w.toFloat(); val fh = h.toFloat()
        Cyber.chamfer(0f, 0f, fw, fh, 1.8f * cq, clip)
        bgP.shader = LinearGradient(0f, 0f, 0f, fh, 0xFF0E0506.toInt(), 0xFF090304.toInt(), Shader.TileMode.CLAMP)
        glowP.shader = RadialGradient(0f, 0f, 1f, intArrayOf(Cyber.alpha(pal.neon, .22f), Cyber.alpha(pal.neon, 0f)), floatArrayOf(0f, .72f), Shader.TileMode.CLAMP)
        val gy = fh * .17f
        fl1.shader = LinearGradient(0f, fh - gy, 0f, fh, Cyber.alpha(pal.neon, .42f), Cyber.alpha(pal.neon, 0f), Shader.TileMode.CLAMP); fl1.strokeWidth = 1f
        fl2.shader = LinearGradient(0f, fh - gy, 0f, fh, Cyber.alpha(pal.neon, .30f), Cyber.alpha(pal.neon, 0f), Shader.TileMode.CLAMP); fl2.strokeWidth = 1f
        val th1 = (fh * .62f).toInt(); val th2 = (fh * .46f).toInt()
        sky1 = sky(7, .2f, 90f, 250f, pal.hot, pal.amber, th1 * 3, th1)
        sky2 = sky(21, .38f, 50f, 170f, pal.neon, pal.amber, th2 * 3, th2)
        linP.shader = LinearGradient(0f, 0f, fw, 0f,
            intArrayOf(Cyber.alpha(pal.neon, 0f), pal.neon, pal.amber, pal.neon, Cyber.alpha(pal.neon, 0f)), floatArrayOf(0f, .1f, .5f, .9f, 1f), Shader.TileMode.CLAMP)
        shadowP.shader = RadialGradient(0f, 0f, 1f, Cyber.alpha(pal.neon, .45f), Cyber.alpha(pal.neon, 0f), Shader.TileMode.CLAMP)
        scanH = fh * .16f
        scanP.shader = LinearGradient(0f, 0f, 0f, scanH, intArrayOf(Cyber.alpha(pal.neon, 0f), Cyber.alpha(pal.neon, .10f), Cyber.alpha(pal.neon, 0f)), null, Shader.TileMode.CLAMP)
        barFg.shader = LinearGradient(0f, 0f, 1f, 0f, pal.hot, pal.amber, Shader.TileMode.CLAMP)
        beamTw = -1f
        streakP.shader = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(Cyber.alpha(pal.neon, 0f), pal.neon, Cyber.alpha(pal.neon, 0f)), null, Shader.TileMode.CLAMP)
    }

    private fun spark(x: Float, y: Float, n: Int, w: Float) {
        for (i in 0 until n) {
            if (pn >= 80) return
            px[pn] = x; py[pn] = y
            pvx[pn] = (30f + rnd.nextFloat() * 190f) * w / 900f
            pvy[pn] = -(70f + rnd.nextFloat() * 190f) * w / 900f
            pl[pn] = .45f + rnd.nextFloat() * .5f; pt[pn] = 0f
            pn++
        }
    }

    private fun drawRim(c: Canvas, cx: Float, cy: Float, u: Float) {
        c.save(); c.translate(cx, cy); c.scale(u, u); c.rotate(ang)
        fun ring(r: Float, sw: Float, a: Float, dash: DashPathEffect? = null) {
            rimP.strokeWidth = sw; rimP.alpha = (255 * a).toInt()
            rimP.pathEffect = dash
            c.drawCircle(0f, 0f, r, rimP)
        }
        ring(88f, 3f, 1f); ring(80f, 1.5f, .55f); ring(70f, 2f, .7f, dashA); ring(121f, 5f, .5f, dashB)
        rimP.pathEffect = null; rimP.strokeWidth = 2.6f; rimP.alpha = 255
        for (i in 0 until 5) { c.save(); c.rotate(i * 72f); rr.set(48f, -17f, 68f, 17f); c.drawRoundRect(rr, 10f, 10f, rimP); c.restore() }
        ring(27f, 3f, 1f); ring(14f, 2f, 1f)
        rimP.alpha = 255
        for (i in 0 until 5) { c.save(); c.rotate(i * 72f + 36f); c.drawCircle(20f, 0f, 3.6f, rimF); c.restore() }
        path.reset(); path.moveTo(0f, -80f); path.lineTo(7f, -66f); path.lineTo(-7f, -66f); path.close()
        c.drawPath(path, rimF)
        c.restore()
    }

    private fun drawWheel(c: Canvas, bmp: Bitmap?, left: Float, top: Float, ww: Float, lift: Float, rh: Float) {
        val sy = 1f - .05f * min(1f, lift / rh)
        c.save()
        c.translate(0f, -lift)
        c.scale(1f, sy, left + ww / 2f, top + ww)
        if (bmp != null) { dstR.set(left, top, left + ww, top + ww); c.drawBitmap(bmp, null, dstR, truckP) }
        drawRim(c, left + ww / 2f, top + ww / 2f, ww / 266f)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (t0 == 0L) { t0 = now }
        if (last == 0L) last = now
        val dt = min(0.05f, (now - last) / 1e9f); last = now
        val el = (now - t0) / 1e6f

        c.save(); c.clipPath(clip)
        c.drawRect(0f, 0f, w, h, bgP)

        val gy = h * .17f
        var tw = w * .92f
        val maxTh = h - gy * .8f - h * .04f
        if (tw * 489f / 1100f > maxTh) tw = maxTh * 1100f / 489f
        val th = tw * 489f / 1100f
        val rw = w * .13f; val rh = rw * .62f
        val span = w + rw; val speed = span / (T / 1000f)
        val cx = -rw / 2f + ((el % T) / T) * span
        val dist = el / 1000f * speed
        val tl = (w - tw) / 2f
        val fx = tl + FRONT * tw; val rx = tl + REAR * tw; val dd = (REAR - FRONT) * tw
        val tf = bump(cx - fx, rw / 2f, rh); val tr = bump(cx - rx, rw / 2f, rh)

        // ---- física: ruedas siguen el terreno; la carrocería cuelga de resortes ----
        val q = dt / 4f
        for (s in 0 until 4) {
            uF += (KT * (tf - wF) - CT * uF) * q; wF += uF * q
            uR += (KT * (tr - wR) - CT * uR) * q; wR += uR * q
            val yF = z + dd / 2f * pit; val yR = z - dd / 2f * pit
            val vF = vz + dd / 2f * vp; val vR = vz - dd / 2f * vp
            val fF = KS * (wF - yF) + CS * (uF - vF); val fR = KS * (wR - yR) + CS * (uR - vR)
            vz += (fF + fR) * q; z += vz * q
            vp += ((dd / 2f) * (fF - fR) / (0.1225f * dd * dd)) * q; pit += vp * q
        }
        if (pf < .5f * rh && tf >= .5f * rh) spark(fx, h - gy - wF, 12, w)
        if (pr < .5f * rh && tr >= .5f * rh) spark(rx, h - gy - wR, 12, w)
        if (pf > .2f * rh && tf < .2f * rh && pf > tf) spark(fx, h - gy, 5, w)
        if (pr > .2f * rh && tr < .2f * rh && pr > tr) spark(rx, h - gy, 5, w)
        pf = tf; pr = tr
        val hum = (sin(el / 38f) * 0.5f + sin(el / 97f) * 0.3f) * d * .5f

        // ---- ciudad al fondo (dos capas con paralaje) ----
        fun skyLayer(b: Bitmap?, hh: Float, k: Float) {
            if (b == null) return
            val tileW = hh * 3f
            var x0 = (dist * k) % tileW - tileW
            val topY = h - gy - hh
            while (x0 < w) { dstR.set(x0, topY, x0 + tileW, topY + hh); c.drawBitmap(b, null, dstR, bmpP); x0 += tileW }
        }
        skyLayer(sky1, h * .62f, .10f)
        skyLayer(sky2, h * .46f, .24f)
        c.save(); c.translate(w * .5f, h * .7f); c.scale(w * .55f, h * .5f); c.drawCircle(0f, 0f, 1f, glowP); c.restore()

        // ---- piso, raya y línea de horizonte ----
        val fy = h - gy
        var xb = (dist % P) - P * (ceil(gy * .5f / P) + 1f)
        while (xb < w + gy * .5f) { c.drawLine(xb + .467f * gy, fy, xb, h, fl1); xb += P }
        for (i in 0 until 4) { val y = h - 1f - i * .28f * gy; c.drawLine(0f, y, w, y, fl2) }
        sparkP.color = Cyber.alpha(pal.neon, .6f)
        var dx = (dist % 44f) - 44f
        while (dx < w) { c.drawRect(dx, fy + 14f * d, dx + 22f, fy + 14f * d + 2f, sparkP); dx += 44f }
        linP.alpha = 70; c.drawRect(0f, fy - 4f * d, w, fy + 4f * d, linP)
        linP.alpha = 255; c.drawRect(0f, fy - 1f, w, fy + 1f, linP)

        // ---- sombra y roca ----
        val sw = .88f * tw; val sh = .05f * tw
        shadowP.alpha = (255 * max(.25f, .85f - z / (2.5f * rh))).toInt().coerceIn(0, 255)
        c.save(); c.translate(tl + .06f * tw + sw / 2f, fy + .025f * tw - sh / 2f); c.scale(sw / 2f, sh / 2f); c.drawCircle(0f, 0f, 1f, shadowP); c.restore()

        c.save(); c.translate(cx - rw / 2f, fy - rh); c.scale(rw / 100f, rh / 62f)
        rockPath.reset(); rockPath.moveTo(2f, 62f); rockPath.lineTo(14f, 36f); rockPath.lineTo(32f, 16f); rockPath.lineTo(50f, 8f)
        rockPath.lineTo(68f, 14f); rockPath.lineTo(86f, 34f); rockPath.lineTo(98f, 62f); rockPath.close()
        rockLine.strokeWidth = 2.2f; rockAmb.strokeWidth = 1.2f; rockRim.strokeWidth = 1.2f
        c.drawPath(rockPath, rockFill); c.drawPath(rockPath, rockLine)
        path.reset(); path.moveTo(32f, 16f); path.lineTo(40f, 38f); path.lineTo(50f, 8f); path.lineTo(56f, 34f); path.lineTo(68f, 14f); c.drawPath(path, rockAmb)
        path.reset(); path.moveTo(14f, 36f); path.lineTo(40f, 38f); path.lineTo(56f, 34f); path.lineTo(86f, 34f); c.drawPath(path, rockRim)
        c.restore()

        // ---- camioneta ----
        val rigTop = h - (gy - .023f * th) - th
        val sc = tw / 1485f
        if (beamTw != tw) {
            beamTw = tw
            beamP.shader = LinearGradient(tl + 70f * sc, 0f, tl - 480f * sc, 0f, Cyber.alpha(pal.amber, .42f), Cyber.alpha(pal.neon, 0f), Shader.TileMode.CLAMP)
        }
        val tk = truck
        val io = (intro?.truckOffset() ?: 0f) * tw
        c.save()
        c.translate(io, 0f)
        c.translate(0f, -z + hum)
        c.rotate(max(-9f, min(9f, pit * 180f / PI.toFloat())), tl + tw * .5f, rigTop + th * .62f)
        beamP.alpha = (255 * (.75f + sin(el / 140f) * .1f)).toInt().coerceIn(0, 255)
        path.reset(); path.moveTo(tl + 70f * sc, rigTop + 335f * sc); path.lineTo(tl - 480f * sc, rigTop + 215f * sc); path.lineTo(tl - 480f * sc, rigTop + 470f * sc); path.close()
        c.drawPath(path, beamP)
        if (tk != null) {
            if (el >= nextGlitch) {
                glitchUntil = el + 90f + rnd.nextFloat() * 120f
                gA = rnd.nextFloat() * 70f; gB = rnd.nextFloat() * (90f - gA / 2f)
                gDx = (if (rnd.nextFloat() < .5f) -1f else 1f) * (1f + rnd.nextFloat() * 1.4f)
                nextGlitch = glitchUntil + 1800f + rnd.nextFloat() * 3200f
            }
            val glitching = el < glitchUntil
            dstR.set(tl, rigTop, tl + tw, rigTop + th)
            if (glitching) dstR.offset(-gDx / 3f / 100f * tw, 0f)
            c.drawBitmap(tk, null, dstR, truckP)
            if (glitching) {
                c.save()
                c.clipRect(tl, rigTop + th * gA / 100f, tl + tw, rigTop + th * (1f - gB / 100f))
                dstR.set(tl + tw * gDx / 100f, rigTop, tl + tw * gDx / 100f + tw, rigTop + th)
                c.drawBitmap(tk, null, dstR, ghostP)
                c.restore()
            }
        }
        c.restore()

        // ruedas (no siguen la inclinación del cuerpo: siguen el terreno)
        ang = (ang - speed / (127f * tw / 1485f) * 180f / PI.toFloat() * dt) % 360f
        val ww = .179f * tw
        c.save(); c.translate(io, 0f)
        drawWheel(c, wheelF, tl + .0741f * tw, rigTop + .583f * th, ww, wF, rh)
        drawWheel(c, wheelR, tl + .689f * tw, rigTop + .583f * th, ww, wR, rh)
        c.restore()

        // ---- chispas ----
        var i = pn - 1
        while (i >= 0) {
            pt[i] += dt
            if (pt[i] > pl[i]) { pn--; px[i] = px[pn]; py[i] = py[pn]; pvx[i] = pvx[pn]; pvy[i] = pvy[pn]; pl[i] = pl[pn]; pt[i] = pt[pn] }
            else {
                pvy[i] += 520f * w / 900f * dt; px[i] += pvx[i] * dt; py[i] += pvy[i] * dt
                sparkP.color = Cyber.alpha(pal.amber, 1f - pt[i] / pl[i])
                c.drawRect(px[i], py[i], px[i] + 2.4f * d, py[i] + 2.4f * d, sparkP)
            }
            i--
        }

        // ---- barrido de escaneo ----
        c.save(); c.translate(0f, -.2f * h + ((el % 6000f) / 6000f) * 1.3f * h); c.drawRect(0f, 0f, w, scanH, scanP); c.restore()

        // ---- destellos tipo glitch ----
        for (st in streaks) {
            val ph = ((el + st[3]) % st[4]) / st[4]
            var a = 0f; var tx = 0f
            for (j in 1 until stP.size) if (ph <= stP[j]) {
                val f = (ph - stP[j - 1]) / (stP[j] - stP[j - 1])
                a = stA[j - 1] + (stA[j] - stA[j - 1]) * f; tx = stX[j - 1] + (stX[j] - stX[j - 1]) * f
                break
            }
            if (a > .01f) {
                val sw = w * st[2]
                c.save(); c.translate(w * st[1] + sw * tx, h * st[0]); c.scale(sw, 1f)
                streakP.alpha = (a * 255f).toInt().coerceIn(0, 255)
                c.drawRect(0f, 0f, 1f, 2f * d, streakP)
                c.restore()
            }
        }

        // ---- HUD: velocidad y suspensión (medidas del diseño, en cq) ----
        hudBig.textSize = 2.6f * cq; hudSmall.textSize = 1.15f * cq
        val v = Math.round(42f + 4f * sin(el / 900f))
        if (v != lastV) lastV = v
        hudBig.setShadowLayer(1f * cq, 0f, 0f, Cyber.alpha(pal.amber, .6f))
        fun base(p: Paint, top: Float, lh: Float): Float { val fm = p.fontMetrics; return top + (lh - (fm.descent - fm.ascent)) / 2f - fm.ascent }
        val top0 = h * .05f
        val l1 = 2.6f * cq * 1.5f
        hudBig.textAlign = Paint.Align.LEFT
        c.drawText("$lastV km/h", w * .04f, base(hudBig, top0, l1), hudBig)
        hudSmall.textAlign = Paint.Align.LEFT
        c.drawText("Velocidad", w * .04f, base(hudSmall, top0 + l1, 1.15f * cq * 1.5f), hudSmall)
        val bw = 11f * cq; val bx = w * .96f - bw
        val l2 = 1.15f * cq * 1.2f
        hudSmall.textAlign = Paint.Align.RIGHT
        c.drawText("Suspensión", w * .96f, base(hudSmall, top0, l2), hudSmall)
        val by = top0 + l2 + .5f * cq; val bh = .7f * cq
        val comp = max(0f, min(1f, (abs(wF - (z + dd / 2f * pit)) + abs(wR - (z - dd / 2f * pit))) / (1.3f * rh)))
        c.drawRect(bx, by, bx + bw, by + bh, barBg)
        c.save(); c.translate(bx, 0f); c.scale(max(.001f, bw * comp), 1f); c.drawRect(0f, by, 1f, by + bh, barFg); c.restore()

        // ---- marcas de esquina ----
        val tk2 = 1.6f * cq
        val mx = w * .022f; val my = h * .04f
        c.drawLine(mx, my, mx + tk2, my, tickP); c.drawLine(mx, my, mx, my + tk2, tickP)
        c.drawLine(w - mx, my, w - mx - tk2, my, tickP); c.drawLine(w - mx, my, w - mx, my + tk2, tickP)
        c.drawLine(mx, h - my, mx + tk2, h - my, tickP); c.drawLine(mx, h - my, mx, h - my - tk2, tickP)
        c.drawLine(w - mx, h - my, w - mx - tk2, h - my, tickP); c.drawLine(w - mx, h - my, w - mx, h - my - tk2, tickP)

        c.restore()
        if (shown) postInvalidateOnAnimation()
    }
}
