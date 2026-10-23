package com.dmwnezes.palanque.sim

import java.time.LocalDate
import kotlin.math.exp

/**
 * Eleição municipal, no calendário real:
 * convenção em 20 de julho (vice e coligação), campanha nas ruas a partir de 16 de agosto,
 * debates na TV em setembro, 1º turno no primeiro domingo de outubro e 2º turno no último,
 * nas cidades com mais de 200 mil eleitores. Pesquisa toda semana.
 */
object Elections {
    private const val RUNOFF_VOTERS = 200_000

    fun conventionDay(s: Sim) = s.dayOf(LocalDate.of(s.termEndYear, 7, 20))
    fun campaignStart(s: Sim) = s.dayOf(LocalDate.of(s.termEndYear, 8, 16))
    fun debateDays(s: Sim) = listOf(s.dayOf(LocalDate.of(s.termEndYear, 9, 3)), s.dayOf(LocalDate.of(s.termEndYear, 9, 24)), s.dayOf(LocalDate.of(s.termEndYear, 10, 20)))

    fun tick(s: Sim) {
        val g = s.game
        val d = s.date
        // eleições gerais no meio do mandato: só um aviso por enquanto
        if (d.year % 4 == 2 && d.monthValue == 8 && d.dayOfMonth == 1 && g.events.none { it.key == "eleicao_geral" })
            g.events += PendingEvent("eleicao_geral", g.day)
        checkPromises(s)

        if (g.day == conventionDay(s)) start(s)
        val e = g.election ?: run {
            // fim de mandato sem disputa (reeleição já usada ou inelegível)
            if (d.monthValue == 12 && d.dayOfMonth == 31 && d.year == s.termEndYear && g.gameOver == null) endOfTerm(s)
            return
        }
        if (e.finished) {
            if (d.monthValue == 12 && d.dayOfMonth == 31 && d.year == s.termEndYear) newTermOrEnd(s)
            return
        }
        if (g.day == campaignStart(s)) {
            e.phase = 1
            s.news("vote", "Começa a campanha nas ruas: ${e.candidates.size - 1} adversários disputam a prefeitura", 2)
        }
        if (e.phase == 1 && g.day < e.runoffDay && (g.day - campaignStart(s)) % 7 == 0) {
            e.polls += poll(s, e)
            momentum(s, e)
            if (g.settings.randomEvents && g.events.isEmpty() && s.rng(616).nextDouble() < 0.2) attack(s, e)
        }
        val dd = debateDays(s)
        if (g.day == dd[0]) startDebate(s, e, 1)
        if (g.day == dd[1]) startDebate(s, e, 2)
        if (g.day == dd[2] && e.round == 1 && e.rival2 >= 0) startDebate(s, e, 3)
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
        val e = Election(
            s.dayOf(s.firstSunday(s.termEndYear, 10)), s.dayOf(s.lastSunday(s.termEndYear, 10)), cands,
            fund = s.eleitores * 1.2, phase = 0, conventionDone = false,
        )
        // três nomes para vice: do seu partido e de aliados próximos
        val near = coalitionOptions(s).filter { it.second }.map { it.first }
        val viceParties = (listOf(s.party) + near).distinct().take(4)
        repeat(3) { i ->
            val p = viceParties[(i + r.nextInt(viceParties.size)) % viceParties.size]
            e.viceOptions += Vice(Content.personName(r), p.id, r.nextInt(g.bairros.size), listOf(2, 3, 3, 4, 5).random(r))
        }
        g.election = e
        s.news("vote", "Convenção do ${s.party.sigla}: escolha o vice e a coligação", 2)
    }

    /** Partidos que aceitam (true) ou recusam (false) se coligar com você. */
    fun coalitionOptions(s: Sim): List<Pair<Party, Boolean>> {
        val g = s.game
        val me = s.party
        val rivals = g.election?.candidates?.filter { !it.player }?.map { it.party }.orEmpty()
        return g.parties.filter { it.id != me.id && !it.custom && it.id !in rivals }.map { p ->
            val dist = Camara.ideoDistance(p.econ, p.social, me.econ, me.social)
            val rel = g.camara.filter { it.party == p.id }.map { it.relation }.average().takeIf { !it.isNaN() } ?: 0.0
            p to (dist <= 0.55 || rel >= 15)
        }.sortedByDescending { it.first.size }
    }

    /** Fecha a convenção: vice e partidos coligados. */
    fun confirmConvention(s: Sim, vice: Int, partners: List<String>) {
        val g = s.game
        val e = g.election ?: return
        val v = e.viceOptions.getOrNull(vice) ?: e.viceOptions.first()
        e.vice = v
        e.partners.clear()
        e.partners += (partners + v.party).filter { it != g.player.party }.distinct()
        val ps = e.partners.map { s.partyOf(it) }
        e.fund += ps.sumOf { s.eleitores * 0.9 * it.size }
        // horário eleitoral dividido pelo tamanho das coligações
        val mine = s.party.size + ps.sumOf { it.size * 0.7 }
        val rivals = e.candidates.filter { !it.player }.sumOf { s.partyOf(it.party).size * 1.3 }
        e.tv = (mine / (mine + rivals)).coerceIn(0.05, 0.8)
        for (c in e.candidates) if (!c.player) c.strength += s.partyOf(c.party).size * 2
        e.conventionDone = true
        s.news("handshake", "Chapa registrada: vice ${v.name} (${s.partyOf(v.party).sigla})" +
            if (e.partners.size > 1) " e ${e.partners.size} partidos na coligação" else "", 1)
    }

    /** Partidos muito diferentes de você na mesma chapa custam credibilidade. */
    fun incoherence(s: Sim, e: Election): Double = e.partners.count {
        val p = s.partyOf(it); Camara.ideoDistance(p.econ, p.social, s.party.econ, s.party.social) > 0.5
    } * 0.8

    /** Força do candidato num bairro. */
    private fun score(s: Sim, e: Election, i: Int, b: Int, noise: Double): Double {
        val c = e.candidates[i]
        if (!c.player) return c.strength + noise
        val vice = e.vice?.let { v -> v.fame * 0.4 + if (v.bairro == b) 3.0 else 0.0 } ?: 0.0
        return s.game.bairros[b].approval + s.game.bairros[b].rallies * 2.5 + e.boost * (0.6 + e.tv * 1.2) + vice - incoherence(s, e)
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
        val among = if (e.round == 1 && e.rival2 >= 0) listOf(e.candidates.indexOfFirst { it.player }, e.rival2) else e.candidates.indices.toList()
        val by = shares(s, e, among, 900 + e.polls.size)
        val pl = e.candidates.indexOfFirst { it.player }
        e.pollBairro = by.map { it[pl] }
        val t = totals(s, by)
        val r = s.rng(31 + e.polls.size)
        val noisy = t.map { if (it == 0.0) 0.0 else (it + (r.nextDouble() - 0.5) * 0.04).coerceAtLeast(0.005) }
        val sum = noisy.sum()
        return noisy.map { it / sum }
    }

    /** Os adversários também fazem campanha: quem tem mais TV cresce um pouco. */
    private fun momentum(s: Sim, e: Election) {
        val r = s.rng(717 + e.polls.size)
        for (c in e.candidates) if (!c.player) c.strength += (r.nextDouble() - 0.5) * 2 + s.partyOf(c.party).size * 0.3 - 0.2
    }

    private fun attack(s: Sim, e: Election) {
        val rivals = e.candidates.indices.filter { !e.candidates[it].player && (e.rival2 < 0 || it == e.rival2) }
        val i = rivals.maxByOrNull { e.candidates[it].strength } ?: return
        val worst = Area.entries.minBy { s.game.ind[it.ordinal] }
        s.game.events += PendingEvent("ataque", s.game.day, area = worst, text = e.candidates[i].name)
    }

    // ───────────── debates ─────────────

    private fun startDebate(s: Sim, e: Election, n: Int) {
        if (n in e.debatesDone || e.finished) return
        val r = s.rng(1300 + n)
        val weak = Area.entries.sortedBy { s.game.ind[it.ordinal] }.take(3).shuffled(r).take(2)
        val other = Area.entries.filter { it !in weak }.random(r)
        e.debateAreas.clear(); e.debateAreas += (weak + other).shuffled(r)
        e.debate = n; e.debateStep = 0; e.debateScore = 0.0
    }

    val DEBATE_OPTIONS = listOf("Mostrar o que já fez", "Prometer melhorar", "Atacar o adversário", "Fugir da pergunta")

    /** Previsão em palavras para cada resposta. */
    fun debateHint(s: Sim, a: Area, opt: Int): Chip = when (opt) {
        0 -> s.game.ind[a.ordinal].let { v -> if (v >= 55) Chip("Área vai bem: convence", true) else if (v >= 45) Chip("Área morna: pouco efeito", true) else Chip("Área vai mal: pode pegar mal", false) }
        1 -> Chip("Ganha agora; vira cobrança", true)
        2 -> Chip("Arriscado", false)
        else -> Chip("Perde um pouco", false)
    }

    fun answerDebate(s: Sim, opt: Int) {
        val g = s.game
        val e = g.election ?: return
        if (e.debate == 0) return
        val a = e.debateAreas[e.debateStep]
        val r = s.rng(1400 + e.debate * 10 + e.debateStep)
        e.debateScore += when (opt) {
            0 -> ((g.ind[a.ordinal] - 50) / 5).coerceIn(-4.0, 4.0)
            1 -> {
                if (g.campaignPromises.none { it.area == a && !it.done })
                    g.campaignPromises += CampaignPromise(a, g.ind[a.ordinal], s.dayOf(LocalDate.of(s.termEndYear + 2, 12, 31)))
                1.5
            }
            2 -> if (r.nextDouble() < 0.55) 3.0 else -3.0
            else -> -1.0
        }
        e.debateStep++
        if (e.debateStep >= e.debateAreas.size) {
            val gain = (e.debateScore / 2).coerceIn(-4.0, 4.0)
            e.boost += gain
            e.debatesDone += e.debate
            s.news("speech", when {
                gain >= 1.5 -> "Você venceu o debate na TV"
                gain <= -1.5 -> "Debate ruim: os adversários levaram a melhor"
                else -> "Debate empatado"
            }, if (gain >= 1.5) 1 else if (gain <= -1.5) 3 else 0)
            e.debate = 0
        }
    }

    /** Promessas de campanha vencem dois anos depois da eleição. */
    private fun checkPromises(s: Sim) {
        val g = s.game
        for (p in g.campaignPromises) if (!p.done && g.day >= p.due) {
            p.done = true
            val kept = g.ind[p.area.ordinal] >= p.base + 5
            s.shift(-1, if (kept) 3.0 else -4.0)
            s.news(if (kept) "check" else "broken", if (kept) "Promessa de campanha cumprida: ${p.area.label.lowercase()}" else "Promessa de campanha quebrada: ${p.area.label.lowercase()}", if (kept) 1 else 3)
        }
    }

    // ───────────── urna ─────────────

    private fun firstRound(s: Sim, e: Election) {
        if (!e.conventionDone) confirmConvention(s, 0, emptyList())
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
        e.finished = true; e.won = won; e.seen = false; e.debate = 0
        s.news(if (won) "trophy" else "defeat", if (won) "Reeleito! Mais quatro anos à frente da prefeitura" else "Derrota nas urnas", if (won) 1 else 3)
    }

    /** Ação de campanha: comício num bairro (até 2 por bairro). */
    fun rally(s: Sim, b: Int): String? {
        val e = s.game.election ?: return "Não há campanha agora"
        if (e.finished) return "A campanha acabou"
        if (e.phase == 0) return "A campanha nas ruas começa em 16 de agosto"
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

    /** Propaganda na TV e nas redes: até 4 inserções; rende mais com mais tempo de TV. */
    fun advertise(s: Sim): String? {
        val e = s.game.election ?: return "Não há campanha agora"
        if (e.finished) return "A campanha acabou"
        if (e.phase == 0) return "A propaganda começa em 16 de agosto"
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
            // a nova Câmara acompanha a sua votação; os coligados viram base
            val pl = e.candidates.indexOfFirst { it.player }
            Camara.elect(s, e.totals.getOrElse(pl) { 0.3 } * 0.45)
            g.camara.filter { it.party in e.partners }.forEach { it.favor += 15; it.relation += 15 }
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
