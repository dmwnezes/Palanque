package com.dmwnezes.palanque.sim

import kotlin.math.min
import kotlin.math.sign

/**
 * Mandato de vereador, deputado estadual ou federal: votar os projetos do governo,
 * propor leis, mandar emendas para o seu reduto e escolher entre base e oposição.
 */
object Legislativo {
    /** Emendas individuais por ano: 1,2% da receita para vereadores, 2% nos estados, valor fixo federal. */
    fun emendaBudget(s: Sim): Double {
        val seats = s.game.camara.size.coerceAtLeast(1)
        return when (s.game.role) {
            Role.VEREADOR -> 0.012 * s.budget().revenue / seats
            Role.DEP_ESTADUAL -> 0.02 * s.budget().revenue / seats
            else -> 37e6
        }
    }

    fun newYear(s: Sim) { if (s.legislates) s.game.emendasLeft = emendaBudget(s) }

    /** Quanto da emenda o governo de fato paga, conforme a sua posição. */
    fun payFactor(s: Sim) = when (s.game.stance) { 1 -> 1.0; 0 -> 0.85; else -> 0.65 }

    fun monthly(s: Sim) {
        val g = s.game
        for (b in g.bairros) b.local *= 0.97
        if (!s.legislates) return
        g.career.capital += (20 - g.career.capital) * 0.01
        val sameParty = if (g.execParty == g.player.party) 25.0 else 0.0
        g.execRel += (g.stance * 30 + sameParty - g.execRel) * 0.1
        g.partyRel += (30 - g.partyRel) * 0.03
        // o governo manda projetos para a casa votar
        if (Camara.execBill(s) == null && s.rng(4545).nextDouble() < 0.5) {
            val law = Laws.forRole(g.role).filter { it.id !in g.laws }.randomOrNull(s.rng(4546)) ?: return
            g.bills += Bill(g.nextId++, law.id, g.day, g.day + 20, author = "exec")
            s.news("gavel", "O ${s.execTitle} envia \"${law.name}\" para votação", 0)
        }
    }

    fun sendEmenda(s: Sim, unit: Int, area: Area, amount: Double): String? {
        val g = s.game
        if (!s.legislates) return "Só parlamentares mandam emendas"
        if (amount <= 0) return null
        if (amount > g.emendasLeft + 1) return "Restam ${Fmt.money(g.emendasLeft)} em emendas neste ano"
        g.emendasLeft -= amount
        val paid = amount * payFactor(s)
        val bs = g.bairros[unit]
        val people = g.pop * bs.popShare
        // na área que mais preocupa os moradores a emenda rende metade a mais
        val gain = min(15.0, 1500 * paid / (people * 1000)) * (if (area == bs.need) 1.5 else 1.0)
        bs.local = min(30.0, bs.local + gain)
        g.legacy[area.ordinal] += min(1.5, paid / (g.pop * area.ref(s.level)) * 20)
        // vereadores e deputados estaduais gastam do orçamento do governo local
        if (g.role != Role.DEP_FEDERAL) g.cash -= paid
        g.emendas += EmendaLog(g.day, unit, area, paid)
        if (g.emendas.size > 60) g.emendas.removeAt(0)
        s.news("money", "Emenda de ${Fmt.money(paid)} para ${area.label.lowercase()} ${Fmt.inB(bs.name)}" +
            if (paid < amount) " (o governo segurou ${Fmt.money(amount - paid)})" else "", 1, unit)
        return null
    }

    fun canSpeak(s: Sim) = s.game.day - s.game.lastSpeech >= 30
    fun canCpi(s: Sim) = s.game.day - s.game.lastCpi >= 365 && s.game.stance != 1

    /** Discurso na tribuna: um por mês. */
    fun speech(s: Sim): String? {
        val g = s.game
        if (!canSpeak(s)) return "O próximo discurso só em ${30 - (g.day - g.lastSpeech)} dias"
        g.lastSpeech = g.day
        g.career.capital = (g.career.capital + 2).coerceAtMost(100.0)
        s.shift(-1, if (g.stance == -1 && g.execApproval < 45) 1.6 else 0.8)
        s.news("speech", "Seu discurso na tribuna repercute", 1)
        return null
    }

    /** Pedir uma CPI contra o governo: uma por ano, fora da base. */
    fun cpi(s: Sim): String? {
        val g = s.game
        if (g.stance == 1) return "Quem é da base não pede CPI contra o próprio governo"
        if (!canCpi(s)) return "Já houve CPI neste ano"
        g.lastCpi = g.day
        g.career.capital = (g.career.capital + 4).coerceAtMost(100.0)
        g.execRel -= 25
        s.shift(-1, if (g.execApproval < 50) 3.0 else 1.0)
        s.news("gavel", "Você protocola uma CPI contra o governo", 2)
        return null
    }

    fun setStance(s: Sim, st: Int) {
        val g = s.game
        if (st == g.stance) return
        g.execRel += (st - g.stance) * 20.0
        g.stance = st
        s.news("handshake", when (st) { 1 -> "Você entra para a base do governo"; -1 -> "Você vai para a oposição"; else -> "Você se declara independente" }, 0)
    }

    fun vote(s: Sim, b: Bill, v: Int) { b.playerVote = v }

    /** Orientação do seu partido num projeto: 1 sim, −1 não. */
    fun orientation(s: Sim, law: LawDef, revoke: Boolean): Int {
        val p = s.party
        val fit = 0.6 - Camara.ideoDistance(p.econ, p.social, law.econ, law.social)
        return (if (revoke) -fit else fit).sign.toInt().let { if (it == 0) 1 else it }
    }

    /** Depois da votação: o eleitor, o partido e o governo reagem ao seu voto. */
    fun afterVote(s: Sim, b: Bill, passed: Boolean) {
        val g = s.game
        if (!s.legislates) return
        val law = Laws.def(g, b.law)
        val v = b.playerVote
        val popular = if (b.revoke) -law.aprov else law.aprov
        if (v != 0) {
            s.shift(-1, v * popular * 0.4)
            g.partyRel += if (v == orientation(s, law, b.revoke)) 2.0 else -8.0
            if (b.author == "exec") {
                if (g.stance == 1) g.execRel += if (v == 1) 4.0 else -10.0
                if (g.stance == -1 && v == 1) g.career.capital -= 1
            }
        }
        if (b.author == "player" && passed) {
            g.career.capital = (g.career.capital + 6).coerceAtMost(100.0)
            s.shift(-1, 2.0)
        }
    }

    /** "Trocar voto": você apoia o colega num projeto dele depois; ele vota com você agora. */
    fun trade(s: Sim, v: Vereador, bill: Int): String? {
        val g = s.game
        if (v.funded == bill) return "Já houve acordo com ele neste projeto"
        if (g.career.capital < 5) return "Falta capital político"
        g.career.capital -= 5
        v.funded = bill; v.favor += 15; v.relation = (v.relation + 15).coerceAtMost(100.0)
        return null
    }
}

/** Prefeitos dos municípios de um estado: aliados ajudam o governador e os deputados do partido. */
object Mayors {
    fun init(s: Sim) {
        val g = s.game
        val r = s.rng(2468)
        val parties = g.parties.filter { !it.custom }
        val tot = parties.sumOf { it.size.toDouble() }
        for (b in g.bairros) {
            var x = r.nextDouble() * tot
            val p = parties.firstOrNull { x -= it.size; x <= 0 } ?: parties.last()
            b.mayorName = Content.personName(r); b.mayorParty = p.id
            b.mayorRel = target(s, b) + (r.nextDouble() - 0.5) * 20
        }
    }

    fun target(s: Sim, b: BairroState): Double {
        val me = s.party
        val p = s.partyOf(b.mayorParty)
        val same = if (p.id == me.id) 30.0 else 0.0
        return same - 40 * Camara.ideoDistance(p.econ, p.social, me.econ, me.social) + 10 + (b.approval - 50) * 0.3
    }

    fun monthly(s: Sim) {
        val g = s.game
        for (b in g.bairros) b.mayorRel += (target(s, b) - b.mayorRel) * 0.08
        // posse dos prefeitos eleitos na eleição municipal
        val d = s.date
        if (d.monthValue == 1 && d.year % 4 == 1) {
            val r = s.rng(1357)
            val parties = g.parties.filter { !it.custom }
            for (b in g.bairros) if (r.nextDouble() < 0.45) {
                b.mayorName = Content.personName(r); b.mayorParty = parties.random(r).id
                b.mayorRel = target(s, b)
            }
            s.news("flag", "Tomam posse os novos prefeitos ${Fmt.ofState(s.state.code, s.state.name)}", 0)
        }
        // prefeito pede verba ao governador
        if (s.governs && g.settings.randomEvents && g.events.isEmpty() && s.rng(1212).nextDouble() < 0.12) {
            val i = g.bairros.indices.maxBy { g.bairros[it].popShare * s.rng(it).nextDouble() }
            g.events += PendingEvent("prefeito_pede", g.day, bairro = i, amount = (g.pop * g.bairros[i].popShare * 25).coerceIn(500_000.0, 60e6))
        }
    }
}
