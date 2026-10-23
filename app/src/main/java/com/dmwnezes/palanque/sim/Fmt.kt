package com.dmwnezes.palanque.sim

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatação em português: dinheiro, pessoas, datas. */
object Fmt {
    private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val MONTHS_FULL = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    private fun dec(v: Double, digits: Int): String {
        val s = "%.${digits}f".format(java.util.Locale.ROOT, v)
        return s.replace('.', ',').removeSuffix(",0").removeSuffix(",00")
    }

    /** R$ 1,2 bi · R$ 35,4 mi · R$ 820 mil · R$ 950 */
    fun money(v: Double): String {
        val a = abs(v)
        val sign = if (v < 0) "−" else ""
        return sign + when {
            a >= 1e9 -> "R$ ${dec(a / 1e9, if (a >= 1e11) 0 else 1)} bi"
            a >= 1e6 -> "R$ ${dec(a / 1e6, if (a >= 1e8) 0 else 1)} mi"
            a >= 1e3 -> "R$ ${(a / 1e3).roundToLong()} mil"
            else -> "R$ ${a.roundToLong()}"
        }
    }

    /** 2,6 milhões · 82,3 mil · 950 */
    fun people(v: Double): String = when {
        v >= 1e6 -> "${dec(v / 1e6, 1)} milhões".replace("1 milhões", "1 milhão")
        v >= 1e3 -> "${dec(v / 1e3, if (v >= 1e5) 0 else 1)} mil"
        else -> v.roundToLong().toString()
    }

    fun pct(v: Double, digits: Int = 0) = "${dec(v * 100, digits)}%"

    fun date(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    fun monthYear(d: LocalDate) = "${MONTHS_FULL[d.monthValue - 1]} de ${d.year}"
    fun shortMonth(d: LocalDate) = MONTHS[d.monthValue - 1]

    /** "+3" · "−2" */
    fun signed(v: Double) = (if (v >= 0) "+" else "−") + abs(v).roundToLong()

    private val FEMININE = listOf("Vila", "Cidade", "Morada", "Lagoa", "Pedra", "Água", "Nova", "Santa", "Nossa", "Zona", "Barra", "Alvorada", "Aurora", "Boa", "Bela")
    private fun fem(bairro: String) = FEMININE.any { bairro.startsWith(it) }

    /** "no Centro" · "na Vila Nova" */
    fun inB(bairro: String) = (if (fem(bairro)) "na " else "no ") + bairro

    /** "o Centro" · "a Vila Nova" */
    fun theB(bairro: String) = (if (fem(bairro)) "a " else "o ") + bairro

    /** "do Centro" · "da Vila Nova" */
    fun ofB(bairro: String) = (if (fem(bairro)) "da " else "do ") + bairro

    /** "do Ceará" · "da Bahia" · "de São Paulo" */
    fun ofState(uf: Int, name: String) = when (uf) {
        11, 14, 26, 27, 28, 31, 35, 42, 50, 51, 52 -> "de "
        25, 29 -> "da "
        else -> "do "
    } + name
}
