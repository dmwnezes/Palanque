package com.dmwnezes.palanque.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.dmwnezes.palanque.R
import com.dmwnezes.palanque.sim.Content
import com.dmwnezes.palanque.world.Geo
import com.dmwnezes.palanque.world.Projection
import com.dmwnezes.palanque.world.Region
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Câmera do mapa em quilômetros: escala (px por km) e deslocamento. */
class MapCamera {
    var scale by mutableFloatStateOf(0f)
    var ox by mutableFloatStateOf(0f)
    var oy by mutableFloatStateOf(0f)
    var viewW = 1f; var viewH = 1f
    private var minX = 0f; private var minY = 0f; private var maxX = 1f; private var maxY = 1f
    private var fitScale = 1f

    fun toWorld(o: Offset) = Offset((o.x - ox) / scale, (o.y - oy) / scale)
    fun sx(x: Float) = ox + x * scale
    fun sy(y: Float) = oy + y * scale

    /** Enquadra a área inteira deixando espaço para as barras de cima e de baixo. */
    fun fit(x0: Float, y0: Float, x1: Float, y1: Float, top: Float = 0.16f, bottom: Float = 0.28f) {
        minX = x0; minY = y0; maxX = x1; maxY = y1
        val usableH = viewH * (1 - top - bottom)
        fitScale = minOf(viewW * 0.9f / (x1 - x0).coerceAtLeast(0.01f), usableH / (y1 - y0).coerceAtLeast(0.01f))
        scale = fitScale
        ox = viewW / 2 - (x0 + x1) / 2 * scale
        oy = viewH * top + usableH / 2 - (y0 + y1) / 2 * scale
    }

    fun zoomAt(c: Offset, f: Float) {
        val ns = (scale * f).coerceIn(fitScale * 0.6f, fitScale * 12f)
        val k = ns / scale
        ox = c.x - (c.x - ox) * k
        oy = c.y - (c.y - oy) * k
        scale = ns
        clamp()
    }

    fun pan(d: Offset) { ox += d.x; oy += d.y; clamp() }

    private fun clamp() {
        // a cidade nunca sai inteira da tela
        ox = ox.coerceIn(viewW * 0.4f - maxX * scale, viewW * 0.6f - minX * scale)
        oy = oy.coerceIn(viewH * 0.35f - maxY * scale, viewH * 0.65f - minY * scale)
    }
}

/** Anéis em km → um Path. */
fun ringsPath(rings: List<FloatArray>): Path = Path().apply {
    for (r in rings) {
        if (r.size < 6) continue
        moveTo(r[0], r[1])
        var k = 2
        while (k < r.size) { lineTo(r[k], r[k + 1]); k += 2 }
        close()
    }
}

@Composable
fun rememberLabelPaint(sizeSp: Float, bold: Boolean = true): Paint {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    return remember(sizeSp, bold) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(ctx, if (bold) R.font.outfit_semibold else R.font.outfit_regular) }.getOrNull() ?: Typeface.DEFAULT
            textSize = with(density) { sizeSp * fontScale * this.density }
            textAlign = Paint.Align.CENTER
        }
    }
}

/** Cor e rótulo de cada bairro para a camada escolhida. */
class BairroPaint(val fill: Color, val label: String?)

/**
 * Mapa da cidade: municípios vizinhos apagados ao fundo, o contorno real da cidade,
 * os bairros pintados pela camada escolhida e as obras como ícones.
 */
@Composable
fun CityMapView(
    c: GameController,
    modifier: Modifier,
    paints: (Int) -> BairroPaint,
    frame: () -> Long,
    onTap: (Int) -> Unit,
) {
    val sim = c.sim
    val plan = sim.plan
    val camera = c.camera
    val place = sim.place
    val proj = remember(place.code, sim.level) { Projection.around(place) }
    val bairroPaths = remember(place.code, sim.level) { plan.bairros.map { ringsPath(it.rings) } }
    val outline = remember(place.code, sim.level) { ringsPath(plan.outline) }
    val neighbors = remember(place.code, sim.level) {
        val padLon = (place.lonMax - place.lonMin) * 0.8f; val padLat = (place.latMax - place.latMin) * 0.8f
        // na cidade, os municípios vizinhos; no estado, os estados vizinhos
        val pool = if (sim.level == com.dmwnezes.palanque.sim.Level.CIDADE) sim.br.cities else sim.br.states
        pool.filter {
            it.code != place.code && it.lonMax > place.lonMin - padLon && it.lonMin < place.lonMax + padLon &&
                it.latMax > place.latMin - padLat && it.latMin < place.latMax + padLat
        }.map { r ->
            val rings = r.rings.map { proj.ring(it) }
            Triple(r.name, ringsPath(rings), Geo.centroid(rings.maxBy { abs(Geo.area(it)) }))
        }
    }
    val icons = (Content.OBRAS + Content.OBRAS_ESTADO).map { it.icon }.distinct().associateWith { rememberVectorPainter(GameIcons.of(it)) }
    val label = rememberLabelPaint(12f)
    val small = rememberLabelPaint(10.5f, bold = false)
    val density = LocalDensity.current
    val dp = density.density

    Canvas(
        modifier
            .pointerInput(place.code, sim.level) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) camera.zoomAt(centroid, zoom)
                    camera.pan(pan)
                }
            }
            .pointerInput(place.code, sim.level) {
                detectTapGestures { o ->
                    val w = camera.toWorld(o)
                    onTap(plan.bairroAt(w.x, w.y))
                }
            },
    ) {
        frame()
        if (camera.scale == 0f || camera.viewW != size.width || camera.viewH != size.height) {
            camera.viewW = size.width; camera.viewH = size.height
            camera.fit(plan.minX, plan.minY, plan.maxX, plan.maxY)
        }
        drawRect(Ink.ocean)
        val s = camera.scale
        withTransform({ translate(camera.ox, camera.oy); scale(s, s, Offset.Zero) }) {
            for ((_, p, _) in neighbors) {
                drawPath(p, Ink.land.copy(alpha = 0.55f))
                drawPath(p, Ink.line.copy(alpha = 0.6f), style = Stroke(1f * dp / s))
            }
            plan.bairros.indices.forEach { i ->
                drawPath(bairroPaths[i], paints(i).fill)
            }
            plan.bairros.indices.forEach { i ->
                drawPath(bairroPaths[i], Ink.ocean.copy(alpha = 0.55f), style = Stroke(1.2f * dp / s, join = StrokeJoin.Round))
            }
            val sel = c.selBairro
            if (sel in plan.bairros.indices) drawPath(bairroPaths[sel], Ink.gold, style = Stroke(3f * dp / s, join = StrokeJoin.Round))
            drawPath(outline, Ink.text.copy(alpha = 0.85f), style = Stroke(2f * dp / s, join = StrokeJoin.Round))
        }

        // nomes das cidades vizinhas
        small.color = Ink.faint.toArgb()
        for ((name, _, pos) in neighbors) {
            val x = camera.sx(pos.first); val y = camera.sy(pos.second)
            if (x in 0f..size.width && y in 0f..size.height) drawContext.canvas.nativeCanvas.drawText(name, x, y, small)
        }

        // obras: ícone num círculo perto do nome do bairro; em andamento, com arco de progresso
        val byBairro = c.game.obras.groupBy { it.bairro }
        val r = 11f * dp
        for ((b, list) in byBairro) {
            val shape = plan.bairros.getOrNull(b) ?: continue
            val bx = camera.sx(shape.cx); val by = camera.sy(shape.cy) + 16f * dp
            val shown = list.takeLast(6)
            shown.forEachIndexed { k, o ->
                val x = bx + (k - (shown.size - 1) / 2f) * (r * 2.2f)
                val y = by + r
                val t = Content.obra(o.type)
                drawCircle(if (o.done) Ink.gold else Ink.panelSolid, r, Offset(x, y))
                if (!o.done) {
                    drawCircle(Ink.line, r, Offset(x, y), style = Stroke(2f * dp))
                    drawArc(Ink.gold, -90f, (360 * o.progress).toFloat(), false, Offset(x - r, y - r), Size(r * 2, r * 2), style = Stroke(2.4f * dp, cap = StrokeCap.Round))
                }
                val p = icons[t.icon] ?: return@forEachIndexed
                val isz = 14f * dp
                translate(x - isz / 2, y - isz / 2) {
                    with(p) { draw(Size(isz, isz), colorFilter = ColorFilter.tint(if (o.done) Ink.onGold else Ink.gold)) }
                }
            }
        }

        // nomes dos bairros: do maior para o menor, pulando os que encostariam num nome já escrito
        val taken = ArrayList<android.graphics.RectF>()
        val order = plan.bairros.indices.sortedWith(compareBy<Int> { it != c.selBairro }.thenByDescending { plan.bairros[it].areaKm2 })
        for (i in order) {
            val b = plan.bairros[i]
            val px = sqrt(b.areaKm2) * s
            if (px < 40f * dp && i != c.selBairro) continue
            val x = camera.sx(b.cx); val y = camera.sy(b.cy)
            val name = c.game.bairros[i].name
            val bp = paints(i)
            val w = maxOf(label.measureText(name), bp.label?.let { small.measureText(it) } ?: 0f) / 2 + 3 * dp
            val box = android.graphics.RectF(x - w, y - 13 * dp, x + w, y + (if (bp.label != null) 17 else 4) * dp)
            if (i != c.selBairro && taken.any { android.graphics.RectF.intersects(it, box) }) continue
            taken += box
            label.color = Color.Black.copy(alpha = 0.55f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(name, x + 1, y + 1, label)
            label.color = (if (i == c.selBairro) Ink.gold else Ink.text).toArgb()
            drawContext.canvas.nativeCanvas.drawText(name, x, y, label)
            bp.label?.let {
                small.color = Ink.text.copy(alpha = 0.85f).toArgb()
                drawContext.canvas.nativeCanvas.drawText(it, x, y + 13f * dp, small)
            }
        }
        scaleBar(this, s, small, dp)
    }
}

/** Régua de distância no canto inferior esquerdo (acima da barra de baixo). */
private fun scaleBar(d: DrawScope, pxPerKm: Float, paint: Paint, dp: Float) {
    val target = 80f * dp / pxPerKm
    val p = 10f.pow(floor(log10(target)))
    val km = listOf(1f, 2f, 5f, 10f).map { it * p }.last { it <= target * 1.2f }
    val w = km * pxPerKm
    val x0 = 16f * dp; val y0 = d.size.height * 0.70f
    d.drawLine(Ink.muted, Offset(x0, y0), Offset(x0 + w, y0), 2f * dp)
    d.drawLine(Ink.muted, Offset(x0, y0 - 4 * dp), Offset(x0, y0 + 4 * dp), 2f * dp)
    d.drawLine(Ink.muted, Offset(x0 + w, y0 - 4 * dp), Offset(x0 + w, y0 + 4 * dp), 2f * dp)
    paint.color = Ink.muted.toArgb()
    val txt = if (km >= 1f) "${if (km % 1f == 0f) km.toInt() else km} km" else "${(km * 1000).toInt()} m"
    d.drawContext.canvas.nativeCanvas.drawText(txt, x0 + w / 2, y0 - 8 * dp, paint)
}

/**
 * Mapa simples de várias regiões (estados do Brasil ou municípios de um estado),
 * com toque para escolher. Sem zoom: cabe inteiro na área.
 */
@Composable
fun RegionPicker(
    regions: List<Region>,
    modifier: Modifier,
    selected: Int = -1,
    highlight: Set<Int> = emptySet(),
    labels: Boolean = false,
    dim: Boolean = false,
    onTap: ((Int) -> Unit)? = null,
) {
    val proj = remember(regions) { Projection.around(regions) }
    val shapes = remember(regions) { regions.map { r -> r.rings.map { proj.ring(it) } } }
    val paths = remember(regions) { shapes.map { ringsPath(it) } }
    val bounds = remember(regions) {
        val xs = shapes.flatMap { rs -> rs.flatMap { r -> r.filterIndexed { i, _ -> i % 2 == 0 } } }
        val ys = shapes.flatMap { rs -> rs.flatMap { r -> r.filterIndexed { i, _ -> i % 2 == 1 } } }
        floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
    val label = rememberLabelPaint(10f)
    var fit by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    val dp = LocalDensity.current.density
    Canvas(
        modifier.let { m ->
            if (onTap == null) m else m.pointerInput(regions) {
                detectTapGestures { o ->
                    val x = (o.x - ox) / fit; val y = (o.y - oy) / fit
                    val i = shapes.indexOfFirst { rs -> rs.any { Geo.contains(it, x, y) } }
                    if (i >= 0) onTap(regions[i].code)
                }
            }
        },
    ) {
        val w = bounds[2] - bounds[0]; val h = bounds[3] - bounds[1]
        fit = minOf(size.width / w, size.height / h) * 0.94f
        ox = (size.width - w * fit) / 2 - bounds[0] * fit
        oy = (size.height - h * fit) / 2 - bounds[1] * fit
        withTransform({ translate(ox, oy); scale(fit, fit, Offset.Zero) }) {
            regions.forEachIndexed { i, r ->
                val on = r.code == selected
                val hi = r.code in highlight
                val fill = when {
                    on -> Ink.gold
                    hi -> Ink.teal.copy(alpha = 0.8f)
                    dim -> Ink.land.copy(alpha = 0.6f)
                    else -> Ink.land
                }
                drawPath(paths[i], fill)
            }
            regions.forEachIndexed { i, _ ->
                drawPath(paths[i], Ink.ocean.copy(alpha = 0.9f), style = Stroke(0.8f * dp / fit, join = StrokeJoin.Round))
            }
            val si = regions.indexOfFirst { it.code == selected }
            if (si >= 0) drawPath(paths[si], Ink.text, style = Stroke(2f * dp / fit, join = StrokeJoin.Round, pathEffect = PathEffect.cornerPathEffect(2f)))
        }
        if (labels) {
            regions.forEachIndexed { i, r ->
                val big = shapes[i].maxBy { abs(Geo.area(it)) }
                val (lx, ly) = Geo.labelPoint(big)
                label.color = (if (r.code == selected) Ink.onGold else Ink.text).copy(alpha = 0.9f).toArgb()
                drawContext.canvas.nativeCanvas.drawText(r.sigla.ifEmpty { r.name }, ox + lx * fit, oy + ly * fit + 4 * dp, label)
            }
        }
    }
}
