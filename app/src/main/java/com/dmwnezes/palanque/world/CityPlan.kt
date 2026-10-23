package com.dmwnezes.palanque.world

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Bairro desenhado: polígonos em km, centro e área. */
class BairroShape(val rings: List<FloatArray>, val cx: Float, val cy: Float, val areaKm2: Float)

/** Geometria de uma cidade: contorno real em km e bairros gerados dentro dele. */
class CityPlan(val outline: List<FloatArray>, val bairros: List<BairroShape>) {
    val minX = outline.minOf { r -> (r.indices step 2).minOf { r[it] } }
    val maxX = outline.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
    val minY = outline.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
    val maxY = outline.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }

    /** Bairro sob o ponto (km), ou -1. */
    fun bairroAt(x: Float, y: Float): Int = bairros.indexOfFirst { b -> b.rings.any { Geo.contains(it, x, y) } }

    companion object {
        /** Quantidade de bairros pela população: 6 numa vila, 16 numa metrópole. */
        fun bairroCount(pop: Float): Int = (6 + 3 * log10(maxOf(pop, 3000f) / 3000f)).roundToInt().coerceIn(6, 16)

        /**
         * Gera os bairros: sementes mais densas perto do centro, duas rodadas de relaxamento
         * e células de Voronoi recortadas pelo contorno real. Sempre igual para a mesma cidade.
         */
        fun build(city: Region, count: Int = bairroCount(city.stat(Stat.POP, 10000f))): CityPlan {
            val proj = Projection.around(city)
            val outline = city.rings.map { proj.ring(it) }
            val rnd = Random(city.code.toLong() * 7919L)
            val main = outline.maxBy { abs(Geo.area(it)) }
            val area = outline.sumOf { abs(Geo.area(it)).toDouble() }.toFloat()
            val (cx0, cy0) = Geo.innerPoint(main)
            val minX = main.filterIndexed { i, _ -> i % 2 == 0 }.min(); val maxX = main.filterIndexed { i, _ -> i % 2 == 0 }.max()
            val minY = main.filterIndexed { i, _ -> i % 2 == 1 }.min(); val maxY = main.filterIndexed { i, _ -> i % 2 == 1 }.max()
            val reach = sqrt(area) * 0.35f

            val seeds = ArrayList<FloatArray>()
            seeds += floatArrayOf(cx0, cy0)
            var tries = 0
            while (seeds.size < count && tries < 20000) {
                tries++
                val x = minX + rnd.nextFloat() * (maxX - minX)
                val y = minY + rnd.nextFloat() * (maxY - minY)
                if (!Geo.contains(main, x, y)) continue
                val r = hypot(x - cx0, y - cy0)
                // a cidade se concentra perto do centro; a zona rural fica com células grandes
                if (rnd.nextFloat() > exp(-r / reach) + 0.12f) continue
                if (seeds.any { hypot(it[0] - x, it[1] - y) < reach * 0.12f }) continue
                seeds += floatArrayOf(x, y)
            }
            // relaxamento leve: aproxima cada semente do centro da sua célula (o Centro fica parado)
            repeat(2) {
                val cells = cells(outline, seeds)
                for (i in 1 until seeds.size) {
                    val big = cells[i].maxByOrNull { abs(Geo.area(it)) } ?: continue
                    val (gx, gy) = Geo.centroid(big)
                    if (Geo.contains(main, gx, gy)) { seeds[i][0] = (seeds[i][0] + gx) / 2; seeds[i][1] = (seeds[i][1] + gy) / 2 }
                }
            }
            val shapes = cells(outline, seeds).mapIndexed { i, rings ->
                val keep = rings.filter { it.size >= 6 && abs(Geo.area(it)) > 1e-6f }
                val big = keep.maxByOrNull { abs(Geo.area(it)) }
                val (gx, gy) = if (i == 0) seeds[0][0] to seeds[0][1] else big?.let { Geo.labelPoint(it) } ?: (seeds[i][0] to seeds[i][1])
                BairroShape(keep, gx, gy, keep.sumOf { abs(Geo.area(it)).toDouble() }.toFloat())
            }.filter { it.rings.isNotEmpty() }
            return CityPlan(outline, shapes)
        }

        /** Estado: contorno real e os municípios reais no lugar dos bairros (mesma ordem de Brasil.citiesOf). */
        fun state(br: Brasil, uf: Int): CityPlan {
            val st = br.state(uf)
            val proj = Projection.around(st)
            val units = br.citiesOf(uf).map { c ->
                val rings = c.rings.map { proj.ring(it) }.filter { it.size >= 6 }
                val big = rings.maxBy { abs(Geo.area(it)) }
                val (lx, ly) = Geo.labelPoint(big)
                BairroShape(rings, lx, ly, rings.sumOf { abs(Geo.area(it)).toDouble() }.toFloat())
            }
            return CityPlan(st.rings.map { proj.ring(it) }, units)
        }

        /** Célula de cada semente = contorno recortado pelos semiplanos mais próximos dela. */
        private fun cells(outline: List<FloatArray>, seeds: List<FloatArray>): List<List<FloatArray>> =
            seeds.indices.map { i ->
                var rings = outline
                for (j in seeds.indices) {
                    if (j == i) continue
                    val ax = seeds[i][0]; val ay = seeds[i][1]; val bx = seeds[j][0]; val by = seeds[j][1]
                    val mx = (ax + bx) / 2; val my = (ay + by) / 2
                    val nx = bx - ax; val ny = by - ay
                    rings = rings.mapNotNull { Geo.clipHalfPlane(it, mx, my, nx, ny) }
                    if (rings.isEmpty()) break
                }
                rings
            }
    }
}

/** Geometria plana de polígonos guardados como [x0, y0, x1, y1, ...]. */
object Geo {
    fun area(r: FloatArray): Float {
        var s = 0f
        val n = r.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            s += r[2 * i] * r[2 * j + 1] - r[2 * j] * r[2 * i + 1]
        }
        return s / 2
    }

    fun centroid(r: FloatArray): Pair<Float, Float> {
        val n = r.size / 2
        var cx = 0.0; var cy = 0.0; var a = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val cr = r[2 * i].toDouble() * r[2 * j + 1] - r[2 * j].toDouble() * r[2 * i + 1]
            a += cr
            cx += (r[2 * i] + r[2 * j]) * cr
            cy += (r[2 * i + 1] + r[2 * j + 1]) * cr
        }
        if (abs(a) < 1e-9) return r[0] to r[1]
        return (cx / (3 * a)).toFloat() to (cy / (3 * a)).toFloat()
    }

    fun contains(r: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = r.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = r[2 * i]; val yi = r[2 * i + 1]; val xj = r[2 * j]; val yj = r[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** Ponto garantidamente dentro: o centroide, ou o ponto interno mais afastado da borda numa grade. */
    fun innerPoint(r: FloatArray): Pair<Float, Float> {
        val c = centroid(r)
        if (contains(r, c.first, c.second) && edgeDistance(r, c.first, c.second) > 0.02f * sqrt(abs(area(r)))) return c
        return labelPoint(r)
    }

    /** Ponto da grade mais longe da borda (bom para escrever o nome). */
    fun labelPoint(r: FloatArray): Pair<Float, Float> {
        val xs = r.filterIndexed { i, _ -> i % 2 == 0 }; val ys = r.filterIndexed { i, _ -> i % 2 == 1 }
        val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
        var best = centroid(r); var bestD = -1f
        val steps = 14
        for (a in 0..steps) for (b in 0..steps) {
            val x = x0 + (x1 - x0) * a / steps; val y = y0 + (y1 - y0) * b / steps
            if (!contains(r, x, y)) continue
            val d = edgeDistance(r, x, y)
            if (d > bestD) { bestD = d; best = x to y }
        }
        return best
    }

    fun edgeDistance(r: FloatArray, x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val n = r.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ax = r[2 * i]; val ay = r[2 * i + 1]; val bx = r[2 * j]; val by = r[2 * j + 1]
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            best = minOf(best, hypot(ax + t * dx - x, ay + t * dy - y))
        }
        return best
    }

    /** Sutherland–Hodgman: mantém o lado do semiplano onde (p − m)·n ≤ 0. */
    fun clipHalfPlane(r: FloatArray, mx: Float, my: Float, nx: Float, ny: Float): FloatArray? {
        val n = r.size / 2
        if (n < 3) return null
        val out = ArrayList<Float>(r.size + 8)
        fun side(i: Int) = (r[2 * i] - mx) * nx + (r[2 * i + 1] - my) * ny
        for (i in 0 until n) {
            val j = (i + 1) % n
            val si = side(i); val sj = side(j)
            val inI = si <= 0f; val inJ = sj <= 0f
            if (inI) { out += r[2 * i]; out += r[2 * i + 1] }
            if (inI != inJ) {
                val t = si / (si - sj)
                out += r[2 * i] + t * (r[2 * j] - r[2 * i])
                out += r[2 * i + 1] + t * (r[2 * j + 1] - r[2 * i + 1])
            }
        }
        return if (out.size >= 6) out.toFloatArray() else null
    }
}
