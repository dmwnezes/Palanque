package com.dmwnezes.palanque.sim

import com.dmwnezes.palanque.world.Brasil
import com.dmwnezes.palanque.world.CityPlan
import com.dmwnezes.palanque.world.Region
import com.dmwnezes.palanque.world.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh
import kotlin.random.Random

/** Orçamento anual (R$) — receitas e despesas no ritmo atual. */
class Budget(
    val iptu: Double, val iss: Double, val itbi: Double, val fpm: Double, val icms: Double, val sus: Double, val fundeb: Double,
    val areas: DoubleArray, val admin: Double, val camara: Double, val interest: Double, val amort: Double, val upkeep: Double, val works: Double,
) {
    val own get() = iptu + iss + itbi
    val revenue get() = own + fpm + icms + sus + fundeb
    /** base dos pisos de saúde e educação: impostos e transferências constitucionais */
    val taxBase get() = own + fpm + icms
    val expense get() = areas.sum() + admin + camara + interest + amort + upkeep + works
    /** gasto com pessoal do executivo (Lei de Responsabilidade Fiscal) */
    val personnel get() = areas.sum() * 0.52 + admin * 0.6
    /** o que conta para o piso: saúde sem o dinheiro do SUS, educação sem metade do Fundeb */
    val healthPiso get() = areas[Area.SAUDE.ordinal] - sus
    val eduPiso get() = areas[Area.EDUCACAO.ordinal] - fundeb * 0.5
    val balance get() = revenue - expense
    fun area(a: Area) = areas[a.ordinal]
}

/** Motor do jogo do prefeito: um passo por dia, fechamento por mês e por ano. */
class Sim(val br: Brasil, val game: Game) {
    val city: Region = br.city(game.cityCode) ?: error("cidade ${game.cityCode} não existe")
    val state: Region = br.state(city.uf)
    val plan: CityPlan = CityPlan.build(city)

    /** Avisa a interface de cada notícia. */
    var listener: ((FeedItem) -> Unit)? = null

    val date: LocalDate get() = START_DATE.plusDays(game.day.toLong())
    fun dateOf(day: Int): LocalDate = START_DATE.plusDays(day.toLong())
    fun dayOf(d: LocalDate) = ChronoUnit.DAYS.between(START_DATE, d).toInt()

    /** Sorteio reproduzível: o mesmo save no mesmo dia dá o mesmo resultado. */
    fun rng(salt: Int = 0) = Random(game.settings.seed * 1_000_003L + game.day * 7919L + salt)

    val party: Party get() = game.parties.first { it.id == game.player.party }
    fun partyOf(id: String) = game.parties.firstOrNull { it.id == id } ?: Content.PARTIES.first()

    val pibPc get() = game.pib / game.pop
    val eleitores get() = game.pop * (city.stat(Stat.ELEITORES, (game.pop * 0.72).toFloat()) / city.stat(Stat.POP, game.pop.toFloat()))

    /** Grandes cidades constroem em lote: uma "obra" de São Paulo são várias unidades. */
    val obraUnits: Int get() = max(1, (game.pop / 300_000.0).roundToInt())

    fun obraCost(t: ObraType) = t.cost * obraUnits
    fun obraServes(t: ObraType) = t.serves * obraUnits
    val maxObras get() = min(12, 3 + (game.pop / 60_000).toInt())
    val activeObras get() = game.obras.count { !it.done }

    // ───────────────────────── orçamento ─────────────────────────

    private fun mod(key: String) = game.modifiers.filter { it.key == key && it.until > game.day }.sumOf { it.value }

    fun budget(g: Game = game): Budget {
        val pop = g.pop
        val pib = g.pib
        val econ = 1.0 + mod("receita")
        val pc = pib / pop
        val iptu = pop * pc * 0.0035 * g.iptu * econ
        val compliance = 0.92 - (g.iss - 2.0) * 0.035
        val iss = pib * 0.45 * g.iss / 100 * compliance * econ
        val itbi = pib * 0.0018 * econ
        val fpm = 247_000.0 * pop.pow(0.477)
        val icms = (pib * 0.017 + pop * 120) * econ
        val sus = pop * 400
        val fundeb = pop * 650
        val prices = if (g.pibPc0 > 0) (pc / g.pibPc0).pow(0.7) else 1.0
        val areas = DoubleArray(Area.entries.size) { i -> Area.entries[i].ref * g.spend[i] * pop * g.costScale * g.wage[i] * prices }
        val admin = pop * 260 * g.costScale * prices
        val camara = 0.06 * (iptu + iss + itbi + fpm + icms)
        val interest = g.debt * 0.009 * 12
        val amort = g.debt / 10
        val upkeep = g.obras.filter { it.done }.sumOf { Content.obra(it.type).cost * obraUnits * 0.06 * it.share }
        val works = g.obras.filter { !it.done }.sumOf { val t = Content.obra(it.type); t.cost * obraUnits * it.share / t.months * 12 }
        return Budget(iptu, iss, itbi, fpm, icms, sus, fundeb, areas, admin, camara, interest, amort, upkeep, works)
    }

    /** Situação dos limites da lei no ano até agora (ou no ritmo atual, se o ano acabou de começar). */
    class Limits(val health: Double, val education: Double, val personnel: Double) {
        val healthOk get() = health >= 0.15
        val educationOk get() = education >= 0.25
        val personnelOk get() = personnel <= 0.54
        val personnelAlert get() = personnel > 0.513
    }

    fun limits(): Limits {
        val b = budget()
        return Limits(b.healthPiso / b.taxBase, b.eduPiso / b.taxBase, b.personnel / b.revenue)
    }

    // ───────────────────────── indicadores ─────────────────────────

    fun efficiency(a: Area): Double = game.secretarios[a.ordinal]?.let { 0.85 + 0.075 * it.competence } ?: 0.9

    /** Pessoas atendidas pelas obras prontas de uma área (toda a cidade ou um bairro). */
    fun coverage(a: Area, bairro: Int = -1): Double = game.obras
        .filter { it.done && (bairro < 0 || it.bairro == bairro) && Content.obra(it.type).area == a }
        .sumOf { obraServes(Content.obra(it.type)) }

    fun spendEffect(a: Area) = 26 * tanh(1.6 * (game.spend[a.ordinal] * efficiency(a) - 1))
    fun obraEffect(a: Area) = 22 * (1 - exp(-coverage(a) / game.pop * 1.2))

    fun target(a: Area): Double {
        val i = a.ordinal
        return (game.base[i] + spendEffect(a) + obraEffect(a) + game.legacy[i] + mod("ind_${a.name}")).coerceIn(2.0, 98.0)
    }

    /** Satisfação geral com os serviços (0..100). */
    fun satisfaction(): Double = Area.entries.sumOf { it.weight * game.ind[it.ordinal] } / Area.entries.sumOf { it.weight }

    /** Termos que puxam a aprovação de um bairro, para mostrar ao jogador. */
    class ApprovalParts(val services: Double, val local: Double, val need: Double, val taxes: Double, val honeymoon: Double, val late: Double, val bump: Double) {
        /** sempre há quem apoie e quem rejeite: a soma é comprimida perto dos extremos */
        val total get() = 50 + 42 * tanh((services + local + need + taxes + honeymoon + late + bump - 50) / 42)
    }

    fun localBonus(b: Int): Double {
        val bs = game.bairros[b]
        val people = game.pop * bs.popShare
        var s = 0.0
        for (o in game.obras) if (o.done && o.bairro == b) {
            val t = Content.obra(o.type)
            val share = min(1.0, obraServes(t) / people)
            s += 6 * share * (if (t.area == bs.need) 2 else 1)
        }
        return min(s, 14.0)
    }

    fun approvalParts(b: Int): ApprovalParts {
        val bs = game.bairros[b]
        val inTerm = game.day - game.termStart
        return ApprovalParts(
            services = satisfaction() - 2,
            local = localBonus(b),
            need = (game.ind[bs.need.ordinal] - 50) * 0.15,
            taxes = -(game.iptu - 1) * 14 - (game.iss - 3) * 2.5 + mod("aprov"),
            honeymoon = 6 * max(0.0, 1 - inTerm / 180.0),
            late = if (game.late) -12.0 else 0.0,
            bump = bs.bump,
        )
    }

    // ───────────────────────── passo diário ─────────────────────────

    fun advanceDay() {
        val g = game
        if (g.gameOver != null) return
        val before = date
        g.day++
        val today = date

        // obras andam enquanto há dinheiro em caixa
        for (o in g.obras) if (!o.done) {
            val t = Content.obra(o.type)
            if (g.cash > 0) o.progress += 1.0 / (t.months * 30.44)
            if (o.progress >= 1.0) inaugurate(o)
        }
        // indicadores se movem devagar: um governo leva anos para mudar uma cidade
        for (a in Area.entries) g.ind[a.ordinal] += (target(a) - g.ind[a.ordinal]) * 0.0016
        for (i in g.bairros.indices) {
            val bs = g.bairros[i]
            bs.approval += (approvalParts(i).total - bs.approval) * 0.006
            bs.bump *= 0.97
        }
        g.approval = g.bairros.sumOf { it.approval * it.popShare }
        g.modifiers.removeAll { it.until <= g.day }

        if (today.monthValue != before.monthValue) closeMonth(before)
        if (today.year != before.year) closeYear(before.year)
        Elections.tick(this)
        if (g.events.isEmpty() && g.day >= g.nextEventDay && g.election?.let { !it.finished } != true) {
            Events.spawn(this)
            g.nextEventDay = g.day + 24 + rng(5).nextInt(30)
        }
    }

    private fun inaugurate(o: Obra) {
        o.done = true; o.progress = 1.0
        val t = Content.obra(o.type)
        val b = game.bairros[o.bairro]
        b.bump += 6.0
        game.promises.filter { !it.kept && it.bairro == o.bairro && it.area == t.area }.forEach { it.kept = true; b.bump += 4.0 }
        news("ribbon", "Inaugurado: ${t.name} ${Fmt.inB(b.name)}", 1, o.bairro)
    }

    private fun closeMonth(month: LocalDate) {
        val g = game
        val b = budget()
        val rev = b.revenue / 12
        val exp = b.expense / 12
        g.cash += rev - exp
        g.debt = max(0.0, g.debt - b.amort / 12)
        for (o in g.obras) if (!o.done) o.paid += Content.obra(o.type).cost * obraUnits * o.share / Content.obra(o.type).months

        g.year.revenue += rev; g.year.taxBase += b.taxBase / 12
        g.year.health += b.healthPiso / 12
        g.year.education += b.eduPiso / 12
        g.year.personnel += b.personnel / 12
        g.year.months++

        val wasLate = g.late
        g.late = g.cash < 0
        if (g.late && !wasLate) news("alert", "Caixa negativo: salários atrasados. Corte gastos ou faça um empréstimo", 3)
        if (!g.late && wasLate) news("money", "Salários em dia de novo", 1)

        // a cidade cresce mais quando vai bem
        val econ = g.ind[Area.ECONOMIA.ordinal]
        g.pop *= 1 + (0.006 + (g.approval - 50) * 0.00004 + (econ - 50) * 0.00008) / 12
        g.pib *= 1 + (0.012 + (econ - 50) * 0.0007 + (g.ind[Area.SEGURANCA.ordinal] - 50) * 0.0002 + mod("pib")) / 12

        for (s in g.secretarios) s?.let {
            it.monthsInOffice++
            // secretário desonesto pode virar escândalo
            if (Crisis.ESCANDALO in g.settings.crises && rng(it.id).nextDouble() < (5 - it.honesty) * 0.006 && g.events.none { e -> e.key == "escandalo" })
                g.events += PendingEvent("escandalo", g.day, area = it.area, text = it.name)
        }
        if (month.monthValue % 6 == 0) Setup.refreshPool(this)
        // promessas vencidas
        for (p in g.promises.filter { !it.kept && it.until <= g.day }) {
            g.bairros[p.bairro].approval -= 6
            news("broken", "Promessa não cumprida ${Fmt.inB(g.bairros[p.bairro].name)}", 2, p.bairro)
        }
        g.promises.removeAll { it.kept || it.until <= g.day }

        val rec = MonthRecord(g.day, g.approval, g.cash, rev, exp, g.pop)
        g.history += rec
        g.lastMonth = rec
        if (g.history.size > 120) g.history.removeAt(0)
    }

    private fun closeYear(year: Int) {
        val g = game
        val y = g.year
        if (y.months >= 6 && y.taxBase > 0) {
            val health = y.health / y.taxBase
            val edu = y.education / y.taxBase
            val pers = y.personnel / y.revenue
            val fails = buildList {
                if (health < 0.15) add("saúde abaixo de 15%")
                if (edu < 0.25) add("educação abaixo de 25%")
                if (pers > 0.54) add("pessoal acima de 54%")
            }
            if (fails.isEmpty()) news("check", "Contas de $year aprovadas pelo Tribunal de Contas", 1)
            else {
                g.rejected++
                for (b in g.bairros) b.approval -= 4
                news("gavel", "Contas de $year rejeitadas: ${fails.joinToString(", ")}" +
                    if (g.rejected >= 2) ". Você ficou inelegível" else ". Mais uma rejeição e você fica inelegível", 3)
            }
        }
        g.year = YearTotals()
    }

    // ───────────────────────── ações do jogador ─────────────────────────

    fun setSpend(a: Area, level: Double) { game.spend[a.ordinal] = level.coerceIn(0.3, 2.0) }
    fun setIptu(v: Double) { game.iptu = v.coerceIn(0.6, 1.6) }
    fun setIss(v: Double) { game.iss = v.coerceIn(2.0, 5.0) }

    /** Inicia uma obra; devolve o motivo se não der. */
    fun build(typeId: String, bairro: Int, share: Double = 1.0): String? {
        val t = Content.obra(typeId)
        if (activeObras >= maxObras && share >= 1.0) return "Limite de $maxObras obras ao mesmo tempo"
        val entrada = obraCost(t) * share * 0.15
        if (share >= 1.0 && game.cash < entrada) return "Falta caixa para a entrada (${Fmt.money(entrada)})"
        game.cash -= entrada
        game.obras += Obra(game.nextId++, t.id, bairro, game.day, paid = entrada, share = share)
        news("build", "Obra iniciada: ${t.name} ${Fmt.inB(game.bairros[bairro].name)}", 0, bairro)
        return null
    }

    fun hire(id: Int): String? {
        val s = game.pool.firstOrNull { it.id == id } ?: return "Candidato não encontrado"
        game.pool.remove(s)
        game.secretarios[s.area.ordinal] = s
        Setup.fillPool(this, s.area)
        news("person", "${s.name} (${partyOf(s.party).sigla}) assume ${s.area.label}", 0)
        return null
    }

    fun fire(a: Area) {
        val s = game.secretarios[a.ordinal] ?: return
        game.secretarios[a.ordinal] = null
        news("person", "${s.name} deixa a secretaria de ${a.label}", 0)
    }

    val debtLimit get() = budget().revenue * 1.2
    val borrowLimit get() = max(0.0, min(budget().revenue * 0.16 - game.year.borrowed, debtLimit - game.debt))

    fun borrow(amount: Double): String? {
        if (amount <= 0) return null
        if (amount > borrowLimit + 1) return "Limite de crédito: ${Fmt.money(borrowLimit)} neste ano"
        game.debt += amount; game.cash += amount; game.year.borrowed += amount
        if (game.late && game.cash >= 0) game.late = false
        news("bank", "Empréstimo de ${Fmt.money(amount)} contratado", 0)
        return null
    }

    fun repay(amount: Double): String? {
        val v = min(amount, min(game.debt, game.cash))
        if (v <= 0) return "Sem caixa ou sem dívida"
        game.debt -= v; game.cash -= v
        news("bank", "Dívida amortizada em ${Fmt.money(v)}", 1)
        return null
    }

    // ───────────────────────── ajudantes ─────────────────────────

    fun news(icon: String, text: String, tone: Int = 0, bairro: Int = -1) {
        val f = FeedItem(game.day, icon, text, tone, bairro)
        game.feed.add(0, f)
        if (game.feed.size > 80) game.feed.removeAt(game.feed.lastIndex)
        listener?.invoke(f)
    }

    /** Muda a aprovação de um bairro (ou de todos, com −1) na hora. */
    fun shift(bairro: Int, delta: Double) {
        if (bairro < 0) game.bairros.forEach { it.approval = (it.approval + delta).coerceIn(1.0, 99.0) }
        else game.bairros[bairro].let { it.approval = (it.approval + delta).coerceIn(1.0, 99.0) }
        game.approval = game.bairros.sumOf { it.approval * it.popShare }
    }

    fun addEffect(key: String, months: Int, value: Double) { game.modifiers += TimedEffect(key, game.day + (months * 30.44).toInt(), value) }

    fun firstSunday(year: Int, month: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY))

    fun lastSunday(year: Int, month: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY))

    /** Próxima eleição municipal (o mandato vai até 31/12 do ano seguinte a ela). */
    val termEndYear get() = dateOf(game.termStart).year + 3
    val electionDate: LocalDate get() = firstSunday(termEndYear, 10)
}
