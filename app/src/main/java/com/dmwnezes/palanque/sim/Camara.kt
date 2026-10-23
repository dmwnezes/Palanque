package com.dmwnezes.palanque.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Lei municipal: efeito nas áreas (alcançado aos poucos), na aprovação, custo anual por habitante
 * e fator na receita do IPTU. econ/social = posição ideológica (−1 esquerda/progressista, +1 direita/conservadora).
 */
class LawDef(
    val id: String,
    val name: String,
    val desc: String,
    val econ: Float,
    val social: Float,
    val ind: Map<Area, Double> = emptyMap(),
    val aprov: Double = 0.0,
    val costPc: Double = 0.0,
    val iptuMul: Double = 1.0,
    val custom: Boolean = false,
)

object Laws {
    val READY = listOf(
        LawDef("tarifa_zero", "Tarifa zero no ônibus", "Ônibus de graça para todos, pago pela prefeitura", -0.6f, -0.2f,
            mapOf(Area.TRANSPORTE to 8.0, Area.ECONOMIA to 1.0), aprov = 3.0, costPc = 120.0),
        LawDef("iptu_progressivo", "IPTU progressivo", "Imóveis caros e terrenos vazios pagam mais", -0.6f, 0f, aprov = -1.0, iptuMul = 1.15),
        LawDef("iptu_idosos", "Isenção de IPTU para idosos", "Aposentados com um imóvel não pagam IPTU", 0f, 0.2f, aprov = 2.0, iptuMul = 0.92),
        LawDef("guarda_armada", "Guarda Municipal armada", "Agentes com porte de arma e mais patrulhas", 0.3f, 0.7f,
            mapOf(Area.SEGURANCA to 5.0), aprov = 1.0, costPc = 15.0),
        LawDef("escola_integral", "Escola em tempo integral", "Alunos da rede municipal ficam o dia todo na escola", -0.2f, 0f,
            mapOf(Area.EDUCACAO to 6.0), aprov = 1.0, costPc = 90.0),
        LawDef("ppp_saneamento", "PPP do saneamento", "Empresa privada investe no esgoto e cobra tarifa", 0.7f, 0f,
            mapOf(Area.SANEAMENTO to 7.0), aprov = -1.5),
        LawDef("coleta_seletiva", "Coleta seletiva obrigatória", "Lixo separado e cooperativas de catadores", -0.2f, -0.3f,
            mapOf(Area.AMBIENTE to 5.0), costPc = 20.0),
        LawDef("alvara_na_hora", "Alvará na hora", "Abrir empresa em um dia, sem papelada", 0.6f, 0f,
            mapOf(Area.ECONOMIA to 5.0)),
        LawDef("fundo_turismo", "Fundo municipal de turismo", "Eventos, divulgação e sinalização turística", 0f, 0f,
            mapOf(Area.TURISMO to 5.0, Area.CULTURA to 1.0), costPc = 15.0),
        LawDef("plano_diretor", "Novo plano diretor", "Regras para onde a cidade pode crescer", -0.1f, -0.1f,
            mapOf(Area.AMBIENTE to 3.0, Area.TRANSPORTE to 2.0, Area.ECONOMIA to -1.0)),
        LawDef("ficha_limpa", "Ficha limpa nos cargos", "Condenados não podem ocupar cargos na prefeitura", 0f, 0.1f, aprov = 2.0),
        LawDef("renda_municipal", "Renda básica municipal", "Auxílio mensal para as famílias mais pobres", -0.8f, -0.2f,
            mapOf(Area.ECONOMIA to 2.0, Area.SAUDE to 1.0), aprov = 4.0, costPc = 150.0),
        LawDef("escola_civico", "Escolas cívico-militares", "Gestão disciplinar com militares da reserva", 0.4f, 0.8f,
            mapOf(Area.EDUCACAO to 2.0, Area.SEGURANCA to 1.0), aprov = 0.5, costPc = 10.0),
        LawDef("cultura_periferia", "Cultura na periferia", "Editais para grupos culturais dos bairros", -0.4f, -0.6f,
            mapOf(Area.CULTURA to 6.0), aprov = 1.0, costPc = 12.0),
    )

    val INTENSITY = listOf("Pequena", "Média", "Grande")

    fun fromCustom(c: CustomLaw): LawDef {
        val k = c.intensity.coerceIn(1, 3)
        val gain = listOf(3.0, 5.0, 8.0)[k - 1]
        val pays = c.funding == 0
        return LawDef(
            c.id, c.name,
            "${c.area.label} +${gain.toInt()} · " + if (pays) "a prefeitura paga" else "paga com nova taxa",
            c.ideo, 0f, mapOf(c.area to gain),
            aprov = if (pays) 0.6 * k else -1.0 * k,
            costPc = if (pays) c.area.ref * listOf(0.05, 0.1, 0.18)[k - 1] else 0.0,
            custom = true,
        )
    }

    fun def(g: Game, id: String): LawDef = READY.firstOrNull { it.id == id } ?: fromCustom(g.customLaws.first { it.id == id })
}

/** Câmara Municipal: vereadores, relação com o prefeito, votações e negociação. */
object Camara {
    /** Número de vereadores pela população (Constituição, art. 29, IV). */
    fun seats(pop: Double): Int {
        val limits = listOf(15e3, 30e3, 50e3, 80e3, 120e3, 160e3, 300e3, 450e3, 600e3, 750e3, 900e3, 1.05e6, 1.2e6, 1.35e6, 1.5e6,
            1.8e6, 2.4e6, 3e6, 4e6, 5e6, 6e6, 7e6, 8e6)
        val i = limits.indexOfFirst { pop <= it }.let { if (it < 0) limits.size else it }
        return 9 + 2 * i
    }

    fun ideoDistance(e1: Float, s1: Float, e2: Float, s2: Float) = (abs(e1 - e2) + abs(s1 - s2)) / 2.0

    /** Para onde a relação de um vereador caminha. */
    fun target(s: Sim, v: Vereador): Double {
        val g = s.game
        val p = s.party
        val same = if (v.party == p.id) 35.0 else 0.0
        val dist = ideoDistance(v.econ, v.social, p.econ, p.social)
        val sec = if (g.secretarios.any { it?.party == v.party }) 12.0 else 0.0
        val reduto = s.localBonus(v.bairro) * 1.5 + (g.bairros[v.bairro].approval - 50) * 0.3
        val cargo = if (v.hasCargo) 15.0 else 0.0
        return (same - 45 * dist + 10 + (g.approval - 50) * 0.6 + sec + reduto + cargo + v.favor).coerceIn(-100.0, 100.0)
    }

    fun stance(v: Vereador) = when {
        v.relation >= 20 -> 1 // base
        v.relation <= -20 -> -1 // oposição
        else -> 0
    }

    /** Monta a Câmara: cadeiras divididas pelo tamanho dos partidos; o seu partido tem bancada própria. */
    fun elect(s: Sim, playerShare: Double = 0.15) {
        val g = s.game
        val r = s.rng(555 + g.termNumber)
        val n = seats(g.pop)
        val parties = g.parties.filter { !it.custom || it.id == g.player.party }
        g.camara.clear()
        g.cargos = 0
        val mine = (n * playerShare.coerceIn(0.05, 0.45)).toInt().coerceAtLeast(1)
        repeat(n) { i ->
            val party = if (i < mine) s.party else {
                val others = parties.filter { it.id != g.player.party }
                val tot = others.sumOf { it.size.toDouble() }
                var x = r.nextDouble() * tot
                others.firstOrNull { x -= it.size; x <= 0 } ?: others.last()
            }
            val b = r.nextInt(g.bairros.size)
            val v = Vereador(
                g.nextId++, Content.personName(r), party.id,
                (party.econ + (r.nextFloat() - 0.5f) * 0.4f).coerceIn(-1f, 1f),
                (party.social + (r.nextFloat() - 0.5f) * 0.4f).coerceIn(-1f, 1f),
                b, 0.0,
            )
            v.relation = (target(s, v) + (r.nextDouble() - 0.5) * 20).coerceIn(-100.0, 100.0)
            g.camara += v
        }
    }

    fun monthly(s: Sim) {
        val g = s.game
        if (g.camara.isEmpty()) elect(s)
        for (v in g.camara) {
            v.favor *= 0.9
            v.relation += (target(s, v) - v.relation) * 0.12
        }
        // CPI quando a oposição domina
        val opp = g.camara.count { stance(it) < 0 }
        if (g.settings.randomEvents && opp * 2 > g.camara.size && g.events.isEmpty() && s.rng(808).nextDouble() < 0.05)
            g.events += PendingEvent("cpi", g.day)
        // cassação: governo impopular e oposição com 2/3
        if (g.approval < 22 && opp * 3 >= g.camara.size * 2 && g.events.none { it.key == "cassacao" } && s.rng(909).nextDouble() < 0.25)
            g.events += PendingEvent("cassacao", g.day)
    }

    /** Lei com custo, efeitos e ideologia — chance de cada vereador votar a favor. */
    fun chance(s: Sim, v: Vereador, law: LawDef, revoke: Boolean): Double {
        val d = ideoDistance(v.econ, v.social, law.econ, law.social)
        val fit = if (revoke) d - 0.6 else 0.6 - d
        val score = v.relation * 0.6 + 35 * fit + (s.game.approval - 50) * 0.4
        return 1 / (1 + exp(-score / 12))
    }

    class Count(val yes: Int, val no: Int, val maybe: Int)

    fun count(s: Sim, b: Bill): Count = count(s, Laws.def(s.game, b.law), b.revoke)

    fun count(s: Sim, law: LawDef, revoke: Boolean): Count {
        var y = 0; var n = 0; var m = 0
        for (v in s.game.camara) {
            val p = chance(s, v, law, revoke)
            when { p >= 0.65 -> y++; p <= 0.35 -> n++; else -> m++ }
        }
        return Count(y, n, m)
    }

    fun pending(s: Sim): Bill? = s.game.bills.firstOrNull { it.status == "tramitando" }

    /** Envia um projeto; a votação acontece em 30 dias. */
    fun send(s: Sim, lawId: String, revoke: Boolean = false): String? {
        val g = s.game
        if (g.bills.any { it.status == "tramitando" }) return "Já há um projeto em votação"
        if (!revoke && lawId in g.laws) return "Essa lei já está em vigor"
        g.bills += Bill(g.nextId++, lawId, g.day, g.day + 30, revoke)
        val name = Laws.def(g, lawId).name
        s.news("gavel", if (revoke) "Projeto para revogar \"$name\" chega à Câmara" else "Projeto \"$name\" chega à Câmara", 0)
        return null
    }

    fun withdraw(s: Sim) {
        val b = s.game.bills.firstOrNull { it.status == "tramitando" } ?: return
        b.status = "retirado"
        s.news("gavel", "Projeto retirado da Câmara", 0)
    }

    fun vote(s: Sim, b: Bill) {
        val g = s.game
        val law = Laws.def(g, b.law)
        val r = s.rng(b.id)
        var yes = 0
        for (v in g.camara) if (r.nextDouble() < chance(s, v, law, b.revoke)) yes++
        b.yes = yes; b.no = g.camara.size - yes
        val ok = yes * 2 > g.camara.size
        b.status = if (ok) "aprovado" else "rejeitado"
        if (ok) { if (b.revoke) g.laws.remove(b.law) else g.laws += b.law }
        s.news(if (ok) "check" else "broken", (if (ok) "Câmara aprova" else "Câmara rejeita") + " \"${law.name}\": $yes a ${b.no}", if (ok) 1 else 2)
    }

    // ───────────── negociação (uma de cada por projeto) ─────────────

    fun emendaCost(s: Sim) = max(150_000.0, s.game.pop * 1.5)
    fun cargoCost(s: Sim) = max(90_000.0, s.game.pop * 0.6)

    fun talk(s: Sim, v: Vereador, bill: Int): String? {
        if (v.talked == bill) return "Vocês já conversaram sobre este projeto"
        v.talked = bill; v.favor += 6; v.relation = (v.relation + 6).coerceAtMost(100.0)
        return null
    }

    fun fund(s: Sim, v: Vereador, bill: Int): String? {
        if (v.funded == bill) return "Já liberou emenda para ele neste projeto"
        val c = emendaCost(s)
        if (s.game.cash < c) return "Falta caixa (${Fmt.money(c)})"
        s.game.cash -= c
        v.funded = bill; v.favor += 18; v.relation = (v.relation + 18).coerceAtMost(100.0)
        return null
    }

    fun cargo(s: Sim, v: Vereador): String? {
        if (v.hasCargo) return "Ele já indicou alguém para a prefeitura"
        v.hasCargo = true; s.game.cargos++; v.relation = (v.relation + 25).coerceAtMost(100.0)
        s.shift(-1, -0.5)
        return null
    }

    fun promise(s: Sim, v: Vereador): String? {
        val g = s.game
        if (v.promised >= g.day - 365) return "Já há uma obra prometida no reduto dele"
        v.promised = g.day; v.favor += 15; v.relation = (v.relation + 15).coerceAtMost(100.0)
        g.promises += Promise(v.bairro, g.bairros[v.bairro].need, g.day + 365, vereador = v.id)
        return null
    }

    /** Votação da cassação: 2/3 da Câmara tiram o prefeito. */
    fun impeach(s: Sim): Boolean {
        val g = s.game
        val r = s.rng(4040)
        val yes = g.camara.count { v -> r.nextDouble() < when (stance(v)) { -1 -> 0.85; 0 -> 0.4; else -> 0.05 } }
        return yes * 3 >= g.camara.size * 2
    }
}
