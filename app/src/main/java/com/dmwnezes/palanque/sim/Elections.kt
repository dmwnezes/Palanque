package com.dmwnezes.palanque.sim

import kotlin.math.exp

/**
 * Eleição municipal: campanha de 16 de agosto até o primeiro domingo de outubro,
 * pesquisa toda semana e segundo turno no último domingo de outubro nas cidades
 * com mais de 200 mil eleitores.
 */
object Elections {
    private const val RUNOFF_VOTERS = 200_000

    fun campaignStart(s: Sim) = s.dayOf(java.time.LocalDate.of(s.termEndYear, 8, 16))

    fun tick(s: Sim) {
        val g = s.game
        val d = s.date
        // eleições gerais no meio do mandato: só um aviso por enquanto
        if (d.year % 4 == 2 && d.monthValue == 8 && d.dayOfMonth == 1 && g.events.none { it.key == "eleicao_geral" })
            g.events += PendingEvent("eleicao_geral", g.day)

        if (g.day == campaignStart(s)) start(s)
        val e = g.election ?: run {
            // fim de mandato sem disputa (reeleição já usada ou inelegível)
            if (d.monthValue == 12 && d.dayOfMonth == 31 && d.year == s.termEndYear && g.gameOver == null) endOfTerm(s)
            return
        }
        if (e.finished) {
            if (d.monthValue == 12 && d.dayOfMonth == 31 && d.year == s.termEndYear) newTermOrEnd(s)
            return
        }
        if (g.day < e.day && (g.day - campaignStart(s)) % 7 == 0) e.polls += poll(s, e)
        if (g.day == e.day) firstRound(s, e)
        if (g.day == e.runoffDay && e.round == 1) runoff(s, e)
    }

    private fun start(s: Sim) {
        val g = s.game
        when {
            g.rejected >= 2 -> {
                s.news("gavel", "Contas rejeitadas duas vezes: você está inelegível e não disputa a eleição", 3)
                return
            }
            g.termNumber >= 2 -> {
                s.news("vote", "Seu segundo mandato termina este ano: a lei não permite uma terceira eleição seguida", 2)
                return
            }
        }
        val r = s.rng(4242)
        val parties = g.parties.filter { it.id != g.player.party && !it.custom }.sortedByDescending { it.size * (0.6 + r.nextDouble()) }
        val n = if (s.eleitores > RUNOFF_VOTERS) 4 else 2 + r.nextInt(2)
        // oposição mais forte contra um governo fraco
        val opp = 49 + (50 - g.approval) * 0.3
        val cands = mutableListOf(Candidate(g.player.name, g.player.party, 0.0, player = true))
        for (i in 0 until n) cands += Candidate(Content.personName(r), parties[i].id, opp - i * 5 + (r.nextDouble() - 0.5) * 8)
        val day = s.dayOf(s.firstSunday(s.termEndYear, 10))
        val runoff = s.dayOf(s.lastSunday(s.termEndYear, 10))
        g.election = Election(day, runoff, cands, fund = s.eleitores * 1.6)
        s.news("vote", "Começa a campanha: ${n} adversários disputam a prefeitura com você", 2)
    }

    /** Força do candidato num bairro. */
    private fun score(s: Sim, e: Election, i: Int, b: Int, noise: Double): Double {
        val c = e.candidates[i]
        return if (c.player) s.game.bairros[b].approval + s.game.bairros[b].rallies * 2.5 + e.boost
        else c.strength + noise
    }

    /** Votos por bairro (frações que somam 1), só entre os candidatos indicados. */
    private fun shares(s: Sim, e: Election, among: List<Int>, salt: Int): List<List<Double>> {
        val r = s.rng(salt)
        return s.game.bairros.indices.map { b ->
            val w = among.map { i -> exp(score(s, e, i, b, (r.nextDouble() - 0.5) * 10) / 10.0) }
            val t = w.sum()
            e.candidates.indices.map { i -> among.indexOf(i).let { k -> if (k < 0) 0.0 else w[k] / t } }
        }
    }

    private fun totals(s: Sim, byBairro: List<List<Double>>) = byBairro[0].indices.map { i ->
        s.game.bairros.indices.sumOf { b -> byBairro[b][i] * s.game.bairros[b].popShare }
    }

    fun poll(s: Sim, e: Election): List<Double> {
        val t = totals(s, shares(s, e, e.candidates.indices.toList(), 900 + e.polls.size))
        val r = s.rng(31 + e.polls.size)
        val noisy = t.map { (it + (r.nextDouble() - 0.5) * 0.04).coerceAtLeast(0.005) }
        val sum = noisy.sum()
        return noisy.map { it / sum }
    }

    private fun firstRound(s: Sim, e: Election) {
        val by = shares(s, e, e.candidates.indices.toList(), 1000)
        val t = totals(s, by)
        e.result = by; e.totals = t; e.round = 1
        val order = t.indices.sortedByDescending { t[it] }
        val top = order[0]
        if (t[top] > 0.5 || s.eleitores <= RUNOFF_VOTERS) finish(s, e, e.candidates[top].player)
        else {
            val pl = e.candidates.indexOfFirst { it.player }
            if (pl !in order.take(2)) finish(s, e, false)
            else {
                e.rival2 = order.take(2).first { it != pl }
                e.candidates[e.rival2].strength += 4 // a oposição se une no 2º turno
                e.seen = false
                s.news("vote", "Segundo turno contra ${e.candidates[e.rival2].name}", 2)
            }
        }
    }

    private fun runoff(s: Sim, e: Election) {
        val pl = e.candidates.indexOfFirst { it.player }
        val by = shares(s, e, listOf(pl, e.rival2), 2000)
        e.runoff = totals(s, by)
        e.result = by
        e.round = 2
        finish(s, e, e.runoff[pl] > e.runoff[e.rival2])
    }

    private fun finish(s: Sim, e: Election, won: Boolean) {
        e.finished = true; e.won = won; e.seen = false
        s.news(if (won) "trophy" else "defeat", if (won) "Reeleito! Mais quatro anos à frente da prefeitura" else "Derrota nas urnas", if (won) 1 else 3)
    }

    /** Ação de campanha: comício num bairro (até 2 por bairro). */
    fun rally(s: Sim, b: Int): String? {
        val e = s.game.election ?: return "Não há campanha agora"
        if (e.finished) return "A campanha acabou"
        val bs = s.game.bairros[b]
        if (bs.rallies >= 2) return "Já foram dois comícios ${Fmt.inB(bs.name)}"
        val cost = rallyCost(s)
        if (e.fund < cost) return "Falta verba de campanha"
        e.fund -= cost; bs.rallies++
        s.news("speech", "Comício ${Fmt.inB(bs.name)}", 1, b)
        return null
    }

    fun rallyCost(s: Sim) = s.eleitores * 0.12
    fun adCost(s: Sim) = s.eleitores * 0.35

    /** Propaganda na TV e nas redes: até 4 inserções. */
    fun advertise(s: Sim): String? {
        val e = s.game.election ?: return "Não há campanha agora"
        if (e.finished) return "A campanha acabou"
        if (e.boost >= 6) return "Já há propaganda no máximo"
        if (e.fund < adCost(s)) return "Falta verba de campanha"
        e.fund -= adCost(s); e.boost += 1.5
        return null
    }

    private fun newTermOrEnd(s: Sim) {
        val g = s.game
        val e = g.election ?: return
        if (e.won) {
            g.termNumber++
            g.termStart = g.day + 1
            g.election = null
            g.bairros.forEach { it.rallies = 0 }
            // a nova Câmara acompanha a sua votação
            val pl = e.candidates.indexOfFirst { it.player }
            Camara.elect(s, e.totals.getOrElse(pl) { 0.3 } * 0.45)
            s.news("flag", "Posse do segundo mandato e da nova Câmara", 1)
        } else {
            g.gameOver = GameOver("derrota", "Fim do mandato",
                "Você entrega a prefeitura em 1º de janeiro. Em breve dará para recomeçar num cargo menor — vereador ou secretário.")
        }
    }

    private fun endOfTerm(s: Sim) {
        val g = s.game
        g.gameOver = if (g.rejected >= 2) GameOver("inelegivel", "Inelegível", "Com as contas rejeitadas, você deixa a prefeitura sem poder disputar eleições por 8 anos.")
        else GameOver("fim", "Missão cumprida", "Oito anos à frente de ${s.city.name}. Disputar deputado, governador e presidente chega nas próximas versões.")
    }
}
