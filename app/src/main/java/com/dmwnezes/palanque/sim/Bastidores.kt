package com.dmwnezes.palanque.sim

import kotlin.math.min

/** Imprensa e redes: dois termômetros que puxam a aprovação (ou o apoio) para cima ou para baixo. */
object Midia {
    /** Pontos de aprovação vindos da mídia: até ±8. */
    fun weight(g: Game) = (g.press - 50) * 0.08 + (g.social - 50) * 0.08

    fun adCost(s: Sim) = s.budget().revenue * 0.004
    fun marketingCost(s: Sim) = s.budget().revenue * 0.0012

    private val RUMORS = listOf(
        "desviou dinheiro da merenda escolar", "tem uma mansão em Miami", "vai acabar com o Bolsa Família",
        "faltou a todas as votações do ano", "fez festa com dinheiro público", "é sócio escondido de uma empreiteira",
    )

    fun monthly(s: Sim) {
        val g = s.game
        val base = if (s.governs) (g.approval - 50) * 0.3 + (s.satisfaction() - 50) * 0.4 else g.career.capital * 0.25 - 5
        val pressT = 50 + base - g.risk * 0.25 - (if (g.late && s.governs) 8 else 0)
        val socialT = 50 + (g.approval - 50) * 0.6 + (if (g.marketing) (if (s.governs) 12 else 6) else 0)
        g.press = (g.press + (pressT - g.press) * 0.12).coerceIn(0.0, 100.0)
        g.social = (g.social + (socialT - g.social) * 0.2).coerceIn(0.0, 100.0)
        // marketing do governo sai do caixa; o de quem não governa é feito pela equipe (rende metade)
        if (g.marketing && s.governs) g.cash -= marketingCost(s)
        val active = g.inOffice || g.running != null
        if (active && g.settings.randomEvents && g.events.isEmpty() && s.rng(3131).nextDouble() < 0.05)
            g.events += PendingEvent("boato", g.day, text = RUMORS.random(s.rng(3132)))
    }

    fun canInterview(s: Sim) = s.game.day - s.game.lastInterview >= 30

    /** Entrevista: rende quando há o que mostrar. Uma por mês. */
    fun interview(s: Sim): String? {
        val g = s.game
        if (!canInterview(s)) return "Próxima entrevista em ${30 - (g.day - g.lastInterview)} dias"
        g.lastInterview = g.day
        val show = if (s.governs) (s.satisfaction() - 50) / 4 else g.career.capital / 15 - 1
        val gain = (show + (s.rng(3141).nextDouble() - 0.5) * 6).coerceIn(-6.0, 8.0)
        g.press = (g.press + gain).coerceIn(0.0, 100.0)
        g.social = (g.social + 2).coerceIn(0.0, 100.0)
        s.news("speech", if (gain >= 0) "Boa entrevista: a imprensa repercute bem" else "Entrevista ruim: você tropeça nas respostas", if (gain >= 0) 1 else 2)
        return null
    }

    fun canAd(s: Sim) = s.governs && s.game.day - s.game.lastAd >= 60

    /** Propaganda do governo na TV e nas redes: a cada dois meses. */
    fun advertise(s: Sim): String? {
        val g = s.game
        if (!s.governs) return "Só o governo faz propaganda oficial"
        if (!canAd(s)) return "Próxima campanha publicitária em ${60 - (g.day - g.lastAd)} dias"
        val c = adCost(s)
        if (g.cash < c) return "Falta caixa (${Fmt.money(c)})"
        g.cash -= c; g.lastAd = g.day
        g.social = (g.social + 8).coerceIn(0.0, 100.0)
        g.press = (g.press - 2).coerceIn(0.0, 100.0)
        s.news("camera", "Propaganda do governo no ar", 1)
        return null
    }

    fun setMarketing(s: Sim, on: Boolean) {
        s.game.marketing = on
        s.news("camera", if (on) "Você contrata uma agência de marketing digital" else "Contrato de marketing digital encerrado", 0)
    }
}

/** Esquema de corrupção: rende agora, sobe o risco de investigação. */
class Scheme(val id: String, val name: String, val desc: String, val gain: String, val risk: Double)

object Esquemas {
    const val COOLDOWN = 45

    fun options(s: Sim): List<Scheme> {
        val g = s.game
        val e = g.election
        return buildList {
            if (s.governs) {
                val o = g.obras.firstOrNull { !it.done }
                if (o != null) add(Scheme("obra", "Superfaturar uma obra", "A empreiteira cobra a mais e devolve parte por fora",
                    "+${Fmt.money(s.obraCost(Content.obra(o.type)) * 0.05)} no caixa 2", 14.0))
                add(Scheme("propina", "Propina de fornecedor", "Contrato de merenda e remédios para a empresa amiga", "+${Fmt.money(s.budget().revenue * 0.003)} no caixa 2", 10.0))
                add(Scheme("mensalinho", "Mesada para a base", "Dinheiro por fora para cada aliado votar com você", "${Camara.houseName(g)} +15 · −${Fmt.money(Camara.emendaCost(s) * 3)}", 16.0))
            }
            if (s.legislates) {
                add(Scheme("emenda", "Vender emenda", "A prefeitura que recebe devolve uma parte a você", "−25% das emendas · caixa 2 com 40% disso", 12.0))
                add(Scheme("mesada", "Mesada do governo", "Votar com o governo em troca de dinheiro por fora", "Governo +20 · +${Fmt.money(Legislativo.emendaBudget(s) * 0.1)} no caixa 2", 12.0))
            }
            if (e != null && !e.finished && e.conventionDone)
                add(Scheme("caixa2", "Caixa 2 na campanha", "Dinheiro de empresários sem declarar à Justiça Eleitoral",
                    "+35% na verba" + if (g.caixa2 > 0) " · usa o caixa 2 de ${Fmt.money(g.caixa2)}" else "", 18.0))
        }
    }

    fun canRun(s: Sim) = s.game.day - s.game.lastScheme >= COOLDOWN

    fun run(s: Sim, id: String): String? {
        val g = s.game
        if (!canRun(s)) return "Muito arriscado agora: espere ${COOLDOWN - (g.day - g.lastScheme)} dias"
        val sc = options(s).firstOrNull { it.id == id } ?: return "Não dá para fazer isso agora"
        when (id) {
            "obra" -> {
                val o = g.obras.first { !it.done }
                val v = s.obraCost(Content.obra(o.type)) * 0.05
                g.cash -= v; g.caixa2 += v
            }
            "propina" -> { g.caixa2 += s.budget().revenue * 0.003; g.legacy[Area.SAUDE.ordinal] -= 0.5 }
            "mensalinho" -> {
                g.cash -= Camara.emendaCost(s) * 3
                g.camara.filter { Camara.stance(it) >= 0 }.forEach { it.favor += 15; it.relation = min(100.0, it.relation + 15) }
            }
            "emenda" -> { val v = Legislativo.emendaBudget(s) * 0.25; val take = min(v, g.emendasLeft); g.emendasLeft -= take; g.caixa2 += take * 0.4 }
            "mesada" -> { g.execRel += 20; g.caixa2 += Legislativo.emendaBudget(s) * 0.1 }
            "caixa2" -> { val e = g.election!!; e.fund += e.fund * 0.35 + g.caixa2; g.caixa2 = 0.0 }
        }
        g.risk = (g.risk + sc.risk).coerceAtMost(100.0)
        g.lastScheme = g.day
        g.lastSchemeName = sc.name.lowercase()
        return null
    }

    /** Chance mensal de alguém descobrir: cresce com o quadrado do risco. */
    fun monthlyChance(g: Game) = (g.risk / 100) * (g.risk / 100) * 0.3

    fun monthly(s: Sim) {
        val g = s.game
        g.risk = (g.risk - 1.2).coerceAtLeast(0.0)
        if (g.risk < 20) g.investigated = 0
        if (g.risk <= 0 || g.events.isNotEmpty() || s.rng(2718).nextDouble() >= monthlyChance(g)) return
        if (g.investigated == 0) {
            g.investigated = 1
            g.events += PendingEvent("denuncia", g.day, text = g.lastSchemeName.ifEmpty { "dinheiro desviado" })
        } else g.events += PendingEvent("operacao", g.day)
    }

    fun convictChance(s: Sim) = (s.game.risk / 110).coerceIn(0.1, 0.9)

    fun trial(s: Sim) {
        val g = s.game
        if (s.rng(1618).nextDouble() < convictChance(s)) {
            g.gameOver = GameOver("preso", "Condenado", "A Justiça condena você por corrupção e lavagem de dinheiro. A carreira termina na cadeia.")
            return
        }
        g.risk *= 0.4; g.investigated = 0
        g.press = (g.press - 5).coerceAtLeast(0.0)
        s.news("gavel", "Falta de provas: o processo contra você é arquivado", 1)
    }

    fun resign(s: Sim) {
        val g = s.game
        g.career.ineligibleUntil = s.date.year + 8
        g.risk *= 0.3; g.investigated = 0
        s.news("flag", "Você renuncia ao mandato e fica inelegível até ${s.date.year + 8}", 3)
        Elections.termEnds(s)
    }
}

/** Manobras: troca de partido e impeachment de quem governa. */
object Manobras {
    /** Janela partidária: março e começo de abril dos anos de eleição. Sem mandato, a qualquer hora. */
    fun windowOpen(s: Sim): Boolean {
        val d = s.date
        return !s.game.inOffice || (d.year % 2 == 0 && (d.monthValue == 3 || (d.monthValue == 4 && d.dayOfMonth <= 5)))
    }

    fun switchBlock(s: Sim): String? {
        val g = s.game
        return when {
            g.election != null -> "Com a candidatura registrada não dá para trocar de partido"
            !windowOpen(s) -> "Só na janela partidária: de 1º de março a 5 de abril dos anos de eleição"
            g.day - g.lastSwitch < 300 -> "Você acabou de trocar de partido"
            else -> null
        }
    }

    fun switchParty(s: Sim, id: String): String? {
        switchBlock(s)?.let { return it }
        val g = s.game
        if (id == g.player.party) return null
        val old = s.party
        g.player = g.player.copy(party = id)
        g.lastSwitch = g.day
        g.career.capital = (g.career.capital - 5).coerceAtLeast(0.0)
        g.partyRel = 40.0
        s.shift(-1, -2.0)
        for (v in g.camara) when (v.party) { old.id -> v.relation -= 20; id -> v.relation += 20 }
        if (!s.governs) g.stance = if (g.execParty == id) 1 else if (g.stance == 1) 0 else g.stance
        s.news("handshake", "Você deixa o ${old.sigla} e se filia ao ${s.party.sigla}", 2)
        return null
    }

    fun impeachBlock(s: Sim): String? {
        val g = s.game
        return when {
            !s.legislates -> "Só parlamentares abrem o processo"
            g.stance != -1 -> "Vá para a oposição antes"
            g.execApproval >= 35 -> "Com ${g.execApproval.toInt()}% de aprovação o ${s.execTitle} ainda tem apoio (precisa estar abaixo de 35%)"
            g.day - g.lastImpeach < 365 -> "Já houve uma tentativa neste ano"
            g.career.capital < 10 -> "Falta capital político (10)"
            else -> null
        }
    }

    /** Votos prováveis pelo afastamento (com o seu). */
    fun impeachVotes(s: Sim): Int {
        val r = s.rng(4141)
        return 1 + s.game.camara.count { v ->
            val rel = Camara.execRelation(s, v)
            r.nextDouble() < when { rel < -5 -> 0.85; rel < 15 -> 0.4; else -> 0.08 }
        }
    }

    fun impeach(s: Sim): String? {
        impeachBlock(s)?.let { return it }
        val g = s.game
        g.lastImpeach = g.day
        g.career.capital -= 10
        val yes = impeachVotes(s)
        val total = g.camara.size + 1
        val title = s.execTitle
        if (yes * 3 >= total * 2) {
            val who = g.execName
            Elections.newExec(s)
            g.career.capital = (g.career.capital + 22).coerceAtMost(100.0)
            s.shift(-1, 3.0)
            s.news("gavel", "Impeachment aprovado por $yes votos: $who deixa o cargo de $title e o vice ${g.execName} assume", 1)
        } else {
            g.execRel -= 30
            s.shift(-1, -1.0)
            s.news("broken", "Pedido de impeachment derrotado: só $yes de $total votos", 2)
        }
        return null
    }
}

/** Golpe de Estado: o presidente fecha o Congresso e governa sem eleições — até o país derrubá-lo. */
object Golpe {
    fun block(s: Sim): String? {
        val g = s.game
        return when {
            g.regime -> "O regime já está instalado"
            !s.governs || s.level != Level.PAIS -> "Só um presidente em exercício pode tentar"
            else -> null
        }
    }

    /** Chance de dar certo: popularidade, base no Congresso e capital político ajudam; imprensa forte atrapalha. */
    fun chance(s: Sim): Double {
        val g = s.game
        val base = g.camara.count { Camara.stance(it) > 0 }.toDouble() / g.camara.size.coerceAtLeast(1)
        return (0.25 + (g.approval - 50) * 0.012 + (base - 0.5) * 0.6 + (g.career.capital - 20) * 0.004 - (g.press - 50) * 0.004).coerceIn(0.05, 0.85)
    }

    fun coup(s: Sim): String? {
        block(s)?.let { return it }
        val g = s.game
        if (s.rng(1964).nextDouble() >= chance(s)) {
            g.gameOver = GameOver("golpe", "Golpe fracassado", "Os quartéis não aderem e o Congresso resiste. Você é preso por tentativa de golpe de Estado.")
            return null
        }
        g.regime = true; g.regimeSince = g.day; g.unrest = 20.0
        g.election = null; g.running = null; g.candidacyPending = false
        g.bills.filter { it.status == "tramitando" }.forEach { it.status = "retirado" }
        // sanções internacionais e fuga de investimento
        s.addEffect("receita", 600, -0.08); s.addEffect("pib", 600, -0.01)
        g.press = 10.0
        s.shift(-1, -8.0)
        s.news("flag", "Golpe: você fecha o Congresso e suspende as eleições. Mandato sem fim", 3)
        return null
    }

    fun years(s: Sim) = (s.game.day - s.game.regimeSince) / 365

    fun repressChance(s: Sim) = (0.75 - s.game.unrest / 250).coerceIn(0.2, 0.75)

    fun repress(s: Sim) {
        val g = s.game
        if (s.rng(2013).nextDouble() >= repressChance(s)) {
            g.gameOver = GameOver("deposto", "Deposto", "Os militares mudam de lado. Você é derrubado depois de ${years(s)} anos de regime.")
            return
        }
        g.unrest = (g.unrest - 35).coerceAtLeast(0.0)
        g.social = (g.social - 12).coerceAtLeast(0.0)
        s.shift(-1, -6.0)
        s.news("siren", "O Exército dispersa os protestos. O regime resiste, por enquanto", 3)
    }

    fun monthly(s: Sim) {
        val g = s.game
        if (!g.regime) return
        // imprensa censurada; a revolta cresce com o povo insatisfeito e com o tempo
        g.press = g.press.coerceAtMost(15.0)
        g.unrest = (g.unrest + (40 - g.approval) * 0.15 + 0.6).coerceIn(0.0, 100.0)
        if (g.unrest >= 60 && g.events.isEmpty() && s.rng(1985).nextDouble() < 0.12 + (g.unrest - 60) / 200)
            g.events += PendingEvent("levante", g.day)
    }
}
