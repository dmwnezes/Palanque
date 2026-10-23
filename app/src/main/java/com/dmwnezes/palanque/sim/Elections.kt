package com.dmwnezes.palanque.sim

import java.time.LocalDate
import kotlin.math.exp

/**
 * Eleições no calendário real. Anos múltiplos de 4 (2032, 2036…) são municipais: vereador e prefeito.
 * Os outros anos pares (2030, 2034…) são gerais: deputados e governador.
 * Em 20 de julho você escolhe o que disputar (convenção); a campanha nas ruas começa em 16 de agosto,
 * os debates (só para cargos executivos) são em setembro, o 1º turno no primeiro domingo de outubro
 * e o 2º turno no último, onde a lei exige. Pesquisa toda semana.
 */
object Elections {
    private const val RUNOFF_VOTERS = 200_000

    /** Ano da eleição em andamento, ou da próxima. */
    fun year(s: Sim): Int {
        s.game.election?.let { return s.dateOf(it.day).year }
        val d = s.date
        return if (d.year % 2 == 0 && d.monthValue <= 10) d.year else d.year + if (d.year % 2 == 0) 2 else 1
    }

    fun isMunicipal(year: Int) = year % 4 == 0

    fun conventionDay(s: Sim, y: Int = year(s)) = s.dayOf(LocalDate.of(y, 7, 20))
    fun campaignStart(s: Sim, y: Int = year(s)) = s.dayOf(LocalDate.of(y, 8, 16))
    fun debateDays(s: Sim, y: Int = year(s)) = listOf(s.dayOf(LocalDate.of(y, 9, 3)), s.dayOf(LocalDate.of(y, 9, 24)), s.dayOf(LocalDate.of(y, 10, 20)))
    fun firstRoundDay(s: Sim, y: Int = year(s)) = s.dayOf(s.firstSunday(y, 10))

    fun tick(s: Sim) {
        val g = s.game
        val d = s.date
        checkPromises(s)

        if (d.year % 2 == 0 && g.election == null) {
            if (g.day == conventionDay(s, d.year) && g.running == null) openCandidacy(s)
            // candidatura feita (ou vinda de outra jurisdição): a convenção começa
            if (g.running != null && !g.candidacyPending && g.day >= conventionDay(s, d.year) && g.day < firstRoundDay(s, d.year)) start(s)
        }
        val e = g.election ?: run {
            // fim de mandato sem eleição ganha
            if (d.monthValue == 12 && d.dayOfMonth == 31 && g.inOffice && d.year == s.termEndYear) termEnds(s)
            return
        }
        val y = s.dateOf(e.day).year
        if (e.finished) {
            if (d.monthValue == 12 && d.dayOfMonth == 31 && d.year == y) settle(s, e)
            return
        }
        if (g.day == campaignStart(s, y)) {
            e.phase = 1
            s.news("vote", if (e.proportional) "Começa a campanha para ${e.office.label.lowercase()}" else "Começa a campanha nas ruas: ${e.candidates.size - 1} adversários na disputa", 2)
        }
        if (e.phase == 1 && g.day < e.runoffDay && (g.day - campaignStart(s, y)) % 7 == 0) {
            e.polls += poll(s, e)
            if (!e.proportional) {
                momentum(s, e)
                if (g.settings.randomEvents && g.events.isEmpty() && s.rng(616).nextDouble() < 0.2) attack(s, e)
            }
        }
        if (!e.proportional) {
            val dd = debateDays(s, y)
            if (g.day == dd[0]) startDebate(s, e, 1)
            if (g.day == dd[1]) startDebate(s, e, 2)
            if (g.day == dd[2] && e.round == 1 && e.rival2 >= 0) startDebate(s, e, 3)
        }
        if (g.day == e.day) firstRound(s, e)
        if (g.day == e.runoffDay && e.round == 1) runoff(s, e)
    }

    // ───────────── candidatura ─────────────

    /** Cargos que você pode disputar na eleição do ano. */
    fun options(s: Sim, y: Int = year(s)): List<Role> {
        val g = s.game
        if (g.career.ineligibleUntil >= y) return emptyList()
        val all = if (isMunicipal(y)) listOf(Role.VEREADOR, Role.PREFEITO)
            else listOf(Role.DEP_ESTADUAL, Role.DEP_FEDERAL, Role.SENADOR, Role.GOVERNADOR, Role.PRESIDENTE)
        // executivo: no máximo dois mandatos seguidos no mesmo cargo; a Presidência pede experiência acima do município
        return all.filter { r -> !(r.executive && g.inOffice && g.role == r && g.termNumber >= 2) && (r != Role.PRESIDENTE || canRunForPresident(g)) }
    }

    /** Para disputar a Presidência é preciso já ter sido governador, senador, deputado federal ou presidente. */
    fun canRunForPresident(g: Game): Boolean {
        val big = setOf(Role.GOVERNADOR, Role.SENADOR, Role.DEP_FEDERAL, Role.PRESIDENTE)
        return (g.inOffice && g.role in big) || g.career.history.any { it.role in big }
    }

    private fun openCandidacy(s: Sim) {
        val g = s.game
        if (options(s).isEmpty()) {
            if (g.career.ineligibleUntil >= year(s)) s.news("gavel", "Inelegível até ${g.career.ineligibleUntil}: você não disputa esta eleição", 3)
            return
        }
        g.candidacyPending = true
        s.news("vote", "Ano de eleição ${if (isMunicipal(year(s))) "municipal" else "geral"}: escolha o que disputar", 2)
    }

    /** Disputar um cargo (ou nenhum, com null). Em outro nível você deixa o mandato atual. */
    fun declare(s: Sim, role: Role?) {
        val g = s.game
        g.candidacyPending = false
        if (role == null) {
            s.news("vote", "Você decide não disputar a eleição deste ano", 0)
            return
        }
        if (role.level != g.level) {
            if (g.inOffice) {
                g.career.history += CareerEntry(g.role, s.place.name, g.termStart, g.day, g.approval)
                s.news("flag", "Você deixa o cargo de ${g.role.label.lowercase()} para disputar ${role.label.lowercase()}", 2)
            }
            s.switchTo = Setup.newGame(
                s.br, g.settings, g.career.homeCity, g.player, g.parties, role = role, career = g.career,
                day = g.day, inOffice = false, running = role, feed = g.feed.take(12),
            ).also { it.career.capital = g.career.capital }
            return
        }
        g.running = role
        start(s)
    }

    private fun start(s: Sim) {
        val g = s.game
        val office = g.running ?: return
        val y = s.date.year
        val day = firstRoundDay(s, y)
        val runoffDay = s.dayOf(s.lastSunday(y, 10))
        if (office.proportional) {
            val fund = (s.eleitores * 0.12 * (0.6 + g.partyRel / 250)).coerceAtLeast(30_000.0)
            g.election = Election(day, runoffDay, mutableListOf(Candidate(g.player.name, g.player.party, 0.0, player = true)),
                fund = fund, phase = 0, conventionDone = true, office = office, proportional = true)
            s.news("vote", "Candidatura a ${office.label.lowercase()} registrada pelo ${s.party.sigla}", 1)
            return
        }
        val r = s.rng(4242)
        val parties = g.parties.filter { it.id != g.player.party && !it.custom }.sortedByDescending { it.size * (0.6 + r.nextDouble()) }.toMutableList()
        val n = if (s.eleitores > RUNOFF_VOTERS) 4 else 2 + r.nextInt(2)
        val incumbent = g.inOffice && g.role == office
        // oposição mais forte contra um governo fraco
        val opp = (if (incumbent) 45 + (50 - g.approval) * 0.35 else 44.0) + difficultyShift(s)
        val cands = mutableListOf(Candidate(g.player.name, g.player.party, 0.0, player = true))
        // quem governa (ou o partido dele) defende o cargo quando não é você
        if (!incumbent && g.execParty.isNotEmpty() && g.execParty != g.player.party) {
            cands += Candidate(if (s.rng(77).nextBoolean()) g.execName else Content.personName(r), g.execParty, 42 + (g.execApproval - 50) * 0.5 + difficultyShift(s))
            parties.removeAll { it.id == g.execParty }
        }
        while (cands.size < n + 1) {
            val p = parties[cands.size - 1]
            // na Presidência, os adversários são os governadores da IA
            val gov = if (office == Role.PRESIDENTE && s.level == Level.PAIS) g.bairros.indices.filter { g.bairros[it].mayorParty == p.id && g.bairros[it].mayorName.isNotEmpty() }
                .maxByOrNull { g.bairros[it].popShare } else null
            val name = gov?.let { "${g.bairros[it].mayorName} (gov. ${s.br.states[it].sigla})" } ?: Content.personName(r)
            cands += Candidate(name, p.id, opp - (cands.size - 1) * 5 + (r.nextDouble() - 0.5) * 8 + (if (gov != null) 2.0 else 0.0))
        }
        val e = Election(day, runoffDay, cands, fund = s.eleitores * 1.2, phase = 0, conventionDone = false, office = office)
        // três nomes para vice: do seu partido e de aliados próximos
        val near = coalitionOptions(s, e).filter { it.second }.map { it.first }
        val viceParties = (listOf(s.party) + near).distinct().take(4)
        repeat(3) { i ->
            val p = viceParties[(i + r.nextInt(viceParties.size)) % viceParties.size]
            e.viceOptions += Vice(Content.personName(r), p.id, r.nextInt(g.bairros.size), listOf(2, 3, 3, 4, 5).random(r))
        }
        g.election = e
        s.news("vote", "Convenção do ${s.party.sigla}: escolha o vice e a coligação", 2)
    }

    /** Partidos que aceitam (true) ou recusam (false) se coligar com você. */
    fun coalitionOptions(s: Sim, e: Election? = s.game.election): List<Pair<Party, Boolean>> {
        val g = s.game
        val me = s.party
        val rivals = e?.candidates?.filter { !it.player }?.map { it.party }.orEmpty()
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

    /** Força do candidato numa região. */
    private fun score(s: Sim, e: Election, i: Int, b: Int, noise: Double): Double {
        val c = e.candidates[i]
        if (!c.player) return c.strength + noise
        val vice = e.vice?.let { v -> v.fame * 0.4 + if (v.bairro == b) 3.0 else 0.0 } ?: 0.0
        return s.game.bairros[b].approval + s.game.bairros[b].rallies * 2.5 + e.boost * (0.6 + e.tv * 1.2) + vice - incoherence(s, e)
    }

    /** Votos por região (frações que somam 1), só entre os candidatos indicados. */
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

    // ───────────── eleição proporcional ─────────────

    /** Fatia dos votos válidos em cada região e o total: (por região, seus votos, quociente eleitoral). */
    fun proportional(s: Sim, e: Election, noise: Double = 0.0): Triple<List<Double>, Double, Double> {
        val g = s.game
        val seats = Camara.constituencySeats(e.office, g).coerceAtLeast(1)
        val valid = s.eleitores * 0.8 * 0.92
        val quotient = valid / seats
        val by = g.bairros.indices.map { b ->
            val sc = (g.bairros[b].approval + g.bairros[b].rallies * 2.5 + e.boost * (0.6 + e.tv)).coerceIn(0.0, 100.0)
            (sc / 100) * (sc / 100) * 3.0 / seats * (1 + noise)
        }
        val votes = g.bairros.indices.sumOf { valid * g.bairros[it].popShare * by[it] }
        return Triple(by, votes, quotient)
    }

    /** Força extra dos adversários conforme a dificuldade. */
    fun difficultyShift(s: Sim) = when (s.game.settings.difficulty) { 0 -> -5.0; 2 -> 4.0; else -> 0.0 }

    /** Parte do quociente que elege sozinho (o resto vem da legenda, se o partido for grande). */
    fun quotientNeeded(s: Sim) = when (s.game.settings.difficulty) { 0 -> 0.45; 2 -> 0.75; else -> 0.6 }

    /** Eleito com 60% do quociente (normal), ou com 2/3 disso se o partido for grande (votos da legenda). */
    fun elected(s: Sim, votes: Double, quotient: Double) =
        votes >= quotientNeeded(s) * quotient || (votes >= quotientNeeded(s) * 0.66 * quotient && s.party.size >= 0.5)

    fun poll(s: Sim, e: Election): List<Double> {
        if (e.proportional) {
            val (by, v, q) = proportional(s, e, (s.rng(31 + e.polls.size).nextDouble() - 0.5) * 0.1)
            e.pollBairro = by
            return listOf(v / q)
        }
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
        for (c in e.candidates) if (!c.player) c.strength += (r.nextDouble() - 0.5) * 2 + s.partyOf(c.party).size * 0.3 - 0.35
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
                    g.campaignPromises += CampaignPromise(a, g.ind[a.ordinal], s.dayOf(LocalDate.of(s.dateOf(e.day).year + 2, 12, 31)))
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

    /** Promessas de campanha vencem dois anos depois da eleição; só valem se você governar. */
    private fun checkPromises(s: Sim) {
        val g = s.game
        for (p in g.campaignPromises) if (!p.done && g.day >= p.due) {
            p.done = true
            if (!s.governs) continue
            val kept = g.ind[p.area.ordinal] >= p.base + 5
            s.shift(-1, if (kept) 3.0 else -4.0)
            s.news(if (kept) "check" else "broken", if (kept) "Promessa de campanha cumprida: ${p.area.label.lowercase()}" else "Promessa de campanha quebrada: ${p.area.label.lowercase()}", if (kept) 1 else 3)
        }
    }

    // ───────────── urna ─────────────

    private fun firstRound(s: Sim, e: Election) {
        if (e.proportional) {
            val (by, v, q) = proportional(s, e)
            e.result = by.map { listOf(it) }
            e.totals = listOf(v, q)
            e.round = 1
            finish(s, e, elected(s, v, q))
            return
        }
        if (!e.conventionDone) confirmConvention(s, 0, emptyList())
        val by = shares(s, e, e.candidates.indices.toList(), 1000)
        val t = totals(s, by)
        e.result = by; e.totals = t; e.round = 1
        val order = t.indices.sortedByDescending { t[it] }
        val top = order[0]
        // senador é eleito em turno único
        if (t[top] > 0.5 || s.eleitores <= RUNOFF_VOTERS || !e.office.executive) finish(s, e, e.candidates[top].player)
        else {
            val pl = e.candidates.indexOfFirst { it.player }
            if (pl !in order.take(2)) finish(s, e, false)
            else {
                e.rival2 = order.take(2).first { it != pl }
                e.candidates[e.rival2].strength += 2 // a oposição se une no 2º turno
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
        val again = s.game.inOffice && s.game.role == e.office
        s.news(if (won) "trophy" else "defeat",
            if (won) (if (again) "Reeleito ${e.office.label.lowercase()}!" else "Eleito ${e.office.label.lowercase()}!") else "Derrota na eleição para ${e.office.label.lowercase()}",
            if (won) 1 else 3)
    }

    /** Ação de campanha: comício numa região (até 2 por região). */
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

    fun rallyCost(s: Sim) = s.eleitores * (if (s.game.election?.proportional == true) 0.02 else 0.12) *
        when (s.level) { Level.CIDADE -> 1.0; Level.ESTADO -> 0.1; Level.PAIS -> 0.15 }
    fun adCost(s: Sim) = s.eleitores * (if (s.game.election?.proportional == true) 0.04 else 0.35)

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

    // ───────────── posse, fim de mandato e fora do cargo ─────────────

    /** 31 de dezembro do ano da eleição: quem venceu toma posse no dia seguinte. */
    private fun settle(s: Sim, e: Election) {
        val g = s.game
        val y = s.date.year
        if (e.won) takeOffice(s, e) else {
            g.election = null; g.running = null
            g.bairros.forEach { it.rallies = 0 }
            if (!g.inOffice || s.termEndYear <= y) termEnds(s)
        }
        // a IA também troca de governo quando o cargo dela vai às urnas
        if (!s.governs && g.inOffice && isMunicipal(y) == g.role.governor.municipal && s.rng(31337).nextBoolean()) newExec(s)
    }

    private fun takeOffice(s: Sim, e: Election) {
        val g = s.game
        val office = e.office
        if (g.inOffice) g.career.history += CareerEntry(g.role, s.place.name, g.termStart, g.day, g.approval)
        val again = g.inOffice && g.role == office
        val wasExec = s.governs
        g.termNumber = if (again) g.termNumber + 1 else 1
        // as rejeições contam por cargo: começa do zero num cargo novo
        if (!again) g.rejected = 0
        g.role = office; g.inOffice = true; g.running = null; g.election = null
        g.termStart = g.day + 1
        g.bairros.forEach { it.rallies = 0 }
        g.bills.filter { it.status == "tramitando" }.forEach { it.status = "retirado" }
        val pl = e.candidates.indexOfFirst { it.player }
        val share = if (e.proportional) 0.15 else e.totals.getOrElse(pl) { 0.3 } * 0.45
        if (office.executive) {
            if (!again) {
                g.secretarios.indices.forEach { g.secretarios[it] = null }
                Setup.refreshPool(s)
                g.cargos = 0
                g.execApproval = g.approval
            }
            Camara.elect(s, share)
            g.camara.filter { it.party in e.partners }.forEach { it.favor += 15; it.relation += 15 }
        } else {
            if (wasExec) newExec(s)
            g.stance = if (g.execParty == g.player.party) 1 else 0
            Camara.elect(s, share)
            g.emendasLeft = Legislativo.emendaBudget(s)
        }
        s.news("flag", if (again) "Posse do ${g.termNumber}º mandato de ${office.label.lowercase()}" else "Posse como ${office.label.lowercase()}", 1)
    }

    /** Novo governo da IA (prefeito, governador ou presidente). */
    fun newExec(s: Sim) {
        val g = s.game
        val r = s.rng(9090)
        g.execParty = g.parties.filter { !it.custom && it.id != g.player.party }.maxBy { it.size * r.nextDouble() }.id
        g.execName = Content.personName(r)
        g.execApproval = 55.0
        g.execRel = 0.0
        if (g.stance == 1) g.stance = 0
    }

    /** O mandato acabou sem um novo cargo. */
    fun termEnds(s: Sim) {
        val g = s.game
        if (g.inOffice) {
            g.career.history += CareerEntry(g.role, s.place.name, g.termStart, g.day, g.approval)
            s.news("flag", "Fim do mandato de ${g.role.label.lowercase()}", 0)
        }
        g.inOffice = false
        g.election = null; g.running = null
        g.gapPending = true
    }

    /** Fora do cargo: pular até a convenção de um ano de eleição. */
    fun nextElections(s: Sim): List<Int> {
        val y = s.date.year
        val first = if (y % 2 == 0 && s.date.monthValue < 7) y else (y + 1) + ((y + 1) % 2)
        return listOf(first, first + 2)
    }

    fun waitUntil(s: Sim, year: Int) {
        val g = s.game
        g.gapPending = false
        g.day = s.dayOf(LocalDate.of(year, 7, 19))
        g.events.clear()
        g.history.clear()
        s.news("vote", "${Fmt.date(s.date)}: a eleição de $year se aproxima", 0)
    }

    fun retire(s: Sim) {
        val g = s.game
        g.gapPending = false
        val h = g.career.history
        g.gameOver = GameOver("fim", "Fim da carreira",
            if (h.isEmpty()) "Você deixa a política." else "Você deixa a política depois de ${h.size} mandato${if (h.size > 1) "s" else ""}: ${h.joinToString(", ") { it.role.label.lowercase() }}.")
    }
}
