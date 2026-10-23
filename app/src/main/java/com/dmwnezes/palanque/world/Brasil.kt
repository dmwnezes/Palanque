package com.dmwnezes.palanque.world

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.cos

/** Indicadores reais guardados em brasil.bin (mesma ordem de tools/build_brasil.py). */
enum class Stat { POP, PIB, PIB_PC, IDH, RENDA, ALFAB, AGUA, ESGOTO, HOMIC, ELEITORES, AREA, DENS, IDADE_MED }

/** Estado ou município: código IBGE, nome, dados e contorno (lon, lat intercalados). */
class Region(
    val code: Int,
    val uf: Int,
    val name: String,
    val sigla: String,
    val capital: Boolean,
    private val stats: FloatArray,
    val rings: List<FloatArray>,
) {
    fun stat(s: Stat): Float = stats.getOrElse(s.ordinal) { Float.NaN }

    /** Valor do indicador ou o padrão, quando a base não tem o dado. */
    fun stat(s: Stat, fallback: Float): Float = stat(s).takeIf { !it.isNaN() } ?: fallback

    val lonMin: Float by lazy { rings.minOf { r -> (r.indices step 2).minOf { r[it] } } }
    val lonMax: Float by lazy { rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } } }
    val latMin: Float by lazy { rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } } }
    val latMax: Float by lazy { rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } } }

    override fun toString() = name
}

/** Brasil real: 27 unidades da federação e 5.570 municípios. */
class Brasil(val states: List<Region>, val outline: List<FloatArray>, val cities: List<Region>) {
    private val byCode = cities.associateBy { it.code }
    private val byUf = cities.groupBy { it.uf }

    fun city(code: Int): Region? = byCode[code]
    fun state(uf: Int): Region = states.first { it.code == uf }
    fun citiesOf(uf: Int): List<Region> = byUf[uf].orEmpty()

    companion object {
        fun load(input: InputStream): Brasil {
            val d = DataInputStream(input.buffered(1 shl 16))
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic) == "PLQ1") { "brasil.bin inválido" }
            val nStats = d.readUnsignedByte()

            fun stats() = FloatArray(nStats) { d.readFloat() }
            fun rings(): List<FloatArray> = List(d.readUnsignedShort()) {
                val n = d.readInt()
                FloatArray(n * 2) { d.readFloat() }
            }

            val states = List(d.readUnsignedShort()) {
                val code = d.readUnsignedByte()
                val sigla = d.readUTF()
                val name = d.readUTF()
                Region(code, code, name, sigla, false, stats(), rings())
            }
            val outline = rings()
            val siglas = states.associate { it.code to it.sigla }
            val cities = List(d.readUnsignedShort()) {
                val code = d.readInt()
                val uf = d.readUnsignedByte()
                val cap = d.readUnsignedByte() == 1
                val name = d.readUTF()
                Region(code, uf, name, siglas[uf] ?: "", cap, stats(), rings())
            }
            return Brasil(states, outline, cities)
        }
    }
}

/**
 * Projeção local equirretangular em quilômetros: x para leste, y para baixo.
 * Boa o bastante para uma cidade, um estado ou o país (com a latitude média da área).
 */
class Projection(val lon0: Double, val lat0: Double) {
    private val kx = 111.32 * cos(lat0 * PI / 180)
    private val ky = 110.57

    fun x(lon: Float) = ((lon - lon0) * kx).toFloat()
    fun y(lat: Float) = (-(lat - lat0) * ky).toFloat()

    /** Anel em lon/lat → anel em km. */
    fun ring(r: FloatArray): FloatArray = FloatArray(r.size) { i -> if (i % 2 == 0) x(r[i]) else y(r[i]) }

    companion object {
        fun around(r: Region) = Projection((r.lonMin + r.lonMax) / 2.0, (r.latMin + r.latMax) / 2.0)
        fun around(rs: List<Region>) = Projection(
            (rs.minOf { it.lonMin } + rs.maxOf { it.lonMax }) / 2.0,
            (rs.minOf { it.latMin } + rs.maxOf { it.latMax }) / 2.0,
        )
    }
}
