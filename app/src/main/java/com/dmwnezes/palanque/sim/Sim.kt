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

/**
 * Orçamento anual (R$) — receitas e despesas no ritmo atual.
 * Nas cidades: iptu, iss, itbi, fpm, cota do icms. Nos estados os mesmos campos guardam
 * IPVA, ICMS, ITCMD, FPE e outras receitas (veja Sim.taxNames).
 */
class Budget(
    val iptu: Double, val iss: Double, val itbi: Double, val fpm: Double, val icms: Double, val sus: Double, val fundeb: Double,
    val areas: DoubleArray, val admin: Double, val camara: Double, val interest: Double, val amort: Double, val upkeep: Double, val works: Double,
    /** leis em vigor e cargos comissionados */
    val laws: Double = 0.0, val cargos: Double = 0.0,
) {
    val own get() = iptu + iss + itbi
    val revenue get() = own + fpm + icms + sus + fundeb
    /** base dos pisos de saúde e educação: impostos e transferências constitucionais */
    val taxBase get() = own + fpm + icms
    val expense get() = areas.sum() + admin + camara + interest + amort + upkeep + works + laws + cargos
    /** gasto com pessoal do executivo (Lei de Responsabilidade Fiscal) */
    val personnel get() = areas.sum() * 0.52 + admin * 0.6
    /** o que conta para o piso: saúde sem o dinheiro do SUS, educação sem metade do Fundeb */
    val healthPiso get() = areas[Area.SAUDE.ordinal] - sus
    val eduPiso get() = areas[Area.EDUCACAO.ordinal] - fundeb * 0.5
    val balance get() = revenue - expense
    fun area(a: Area) = areas[a.ordinal]
}

/**
 * Motor do jogo: um passo por dia, fechamento por mês e por ano.
 * A jurisdição é uma cidade (bairros gerados) ou um estado (municípios reais).
 * Quando você governa, decide o orçamento; quando é parlamentar ou candidato, o governo é da IA
 * e a aprovação dos bairros/municípios passa a ser o seu apoio eleitoral.
 */
class Sim(val br: Brasil, val game: Game) {
    val level: Level get() = game.level
    /** cidade natal (nas cidades, a própria jurisdição) */
    val city: Region = br.city(game.cityCode) ?: error("cidade ${game.cityCode} não existe")
    val state: Region = br.state(game.uf)
    /** a jurisdição: a cidade ou o estado */
    val place: Region get() = if (level == Level.CIDADE) city else state
    val plan: CityPlan = if (game.level == Level.CIDADE) CityPlan.build(city) else CityPlan.state(br, game.uf)

    /** Avisa a interface de cada notícia. */
    var listener: ((FeedItem) -> Unit)? = null

    /** Jogo novo para outra jurisdição (candidatura em outro nível). A interface troca para ele. */
    var switchTo: Game? = null

    init {
        Fmt.plainNames = if (game.level == Level.ESTADO) game.bairros.map { it.name }.toSet() else emptySet()
        // saves antigos não tinham Câmara
        if (game.camara.isEmpty() && game.bairros.isNotEmpty() && game.pop > 0) Camara.elect(this)
    }

    val date: LocalDate get() = START_DATE.plusDays(game.day.toLong())
    fun dateOf(day: Int): LocalDate = START_DATE.plusDays(day.toLong())
    fun dayOf(d: LocalDate) = ChronoUnit.DAYS.between(START_DATE, d).toInt()

    /** Sorteio reproduzível: o mesmo save no mesmo dia dá o mesmo resultado. */
    fun rng(salt: Int = 0) = Random(game.settings.seed * 1_000_003L + game.day * 7919L + salt)

    val party: Party get() = game.parties.first { it.id == game.player.party }
    fun partyOf(id: String) = game.parties.firstOrNull { it.id == id } ?: Content.PARTIES.first()

    /** Você governa esta jurisdição (prefeito ou governador em exercício). */
    val governs: Boolean get() = game.inOffice && game.role.executive
    /** Você é parlamentar em exercício. */
    val legislates: Boolean get() = game.inOffice && !game.role.executive

    val pibPc get() = game.pib / game.pop
    val eleitores get() = game.pop * (place.stat(Stat.ELEITORES, (game.pop * 0.72).toFloat()) / place.stat(Stat.POP, game.pop.toFloat()))

    /** Palavras do nível: "bairro"/"município", nomes dos impostos e do governo. */
    val unitName get() = if (level == Level.CIDADE) "bairro" else "município"
    val unitsName get() = if (level == Level.CIDADE) "bairros" else "municípios"
    val governmentName get() = if (level == Level.CIDADE) "Prefeitura de ${city.name}" else "Governo ${Fmt.ofState(state.code, state.name)}"
    val execTitle get() = (if (game.role == Role.DEP_FEDERAL) Role.PRESIDENTE else game.role.governor).label.lowercase()

    /** Grandes jurisdições constroem em lote: uma "obra" de São Paulo são várias unidades. */
    val obraUnits: Int get() = max(1, (game.pop / (if (level == Level.CIDADE) 300_000.0 else 3_000_000.0)).roundToInt())
    val obras: List<ObraType> get() = Content.obrasFor(level)

    fun obraCost(t: ObraType) = t.cost * obraUnits
    fun obraServes(t: ObraType) = t.serves * obraUnits
    val maxObras get() = if (level == Level.CIDADE) min(12, 3 + (game.pop / 60_000).toInt()) else min(14, 4 + (game.pop / 1_500_000).toInt())
    val activeObras get() = game.obras.count { !it.done }

    // ───────────────────────── orçamento ─────────────────────────

    private fun mod(key: String) = game.modifiers.filter { it.key == key && it.until > game.day }.sumOf { it.value }

    /** Faixa e rótulos dos dois impostos ajustáveis. */
    val taxBRange get() = if (level == Level.CIDADE) 2.0..5.0 else 17.0..23.0
    val taxANames get() = if (level == Level.CIDADE) "IPTU" to "imposto sobre imóveis" else "IPVA" to "imposto sobre veículos"
    val taxBNames get() = if (level == Level.CIDADE) "ISS (serviços)" to "imposto sobre serviços" else "ICMS" to "imposto sobre mercadorias (já sem a parte das prefeituras)"

    fun budget(g: Game = game): Budget {
        val pop = g.pop
        val pib = g.pib
        val econ = 1.0 + mod("receita")
        val pc = pib / pop
        val active = g.laws.map { Laws.def(g, it) }
        val propMul = active.fold(1.0) { a, l -> a * l.iptuMul }
        val prices = if (g.pibPc0 > 0) (pc / g.pibPc0).pow(0.7) else 1.0
        val areas = DoubleArray(Area.entries.size) { i -> Area.entries[i].ref(level) * g.spend[i] * pop * g.costScale * g.wage[i] * prices }
        val interest = g.debt * 0.009 * 12
        val amort = g.debt / 10
        val upkeep = g.obras.filter { it.done }.sumOf { Content.obra(it.type).cost * obraUnits * 0.06 * it.share }
        val works = g.obras.filter { !it.done }.sumOf { val t = Content.obra(it.type); t.cost * obraUnits * it.share / t.months * 12 }
        val laws = pop * active.sumOf { it.costPc } * g.costScale * prices
        val cargos = g.cargos * max(90_000.0, pop * (if (level == Level.CIDADE) 0.6 else 0.06)) * prices
        if (level == Level.CIDADE) {
            val iptu = pop * pc * 0.0035 * g.iptu * econ * propMul
            val compliance = 0.92 - (g.iss - 2.0) * 0.035
            val iss = pib * 0.45 * g.iss / 100 * compliance * econ
            val itbi = pib * 0.0018 * econ
            val fpm = 247_000.0 * pop.pow(0.477)
            val icms = (pib * 0.017 + pop * 120) * econ
            val admin = pop * 260 * g.costScale * prices
            val camara = 0.06 * (iptu + iss + itbi + fpm + icms)
            return Budget(iptu, iss, itbi, fpm, icms, pop * 400, pop * 650, areas, admin, camara, interest, amort, upkeep, works, laws, cargos)
        }
        // estado: ICMS é o grosso; um quarto dele vai para as prefeituras
        val ipva = pop * pc * 0.0022 * g.iptu * econ * propMul
        val compliance = 0.95 - (g.iss - 18.0) * 0.02
        val icms = pib * 0.075 * (g.iss / 18.0) * compliance * econ * 0.75
        val itcmd = pib * 0.0008 * econ
        // o FPE favorece Norte e Nordeste
        val fpe = pop * (if (g.uf in 11..29) 1350.0 else 280.0)
        val other = pib * 0.004
        val admin = pop * 220 * g.costScale * prices
        val poderes = 0.12 * (ipva + icms + itcmd + fpe)
        return Budget(ipva, icms, itcmd, fpe, other, pop * 250, pop * 420, areas, admin, poderes, interest, amort, upkeep, works, laws, cargos)
    }

    /** Pisos e teto do nível: município (15%, 25%, 54%) ou estado (12%, 25%, 49%). */
    val healthFloor get() = if (level == Level.CIDADE) 0.15 else 0.12
    val personnelCap get() = if (level == Level.CIDADE) 0.54 else 0.49

    class Limits(val health: Double, val education: Double, val personnel: Double, val healthFloor: Double, val personnelCap: Double) {
        val healthOk get() = health >= healthFloor
        val educationOk get() = education >= 0.25
        val personnelOk get() = personnel <= personnelCap
        val personnelAlert get() = personnel > personnelCap * 0.95
    }

    fun limits(): Limits {
        val b = budget()
        return Limits(b.healthPiso / b.taxBase, b.eduPiso / b.taxBase, b.personnel / b.revenue, healthFloor, personnelCap)
    }

    // ───────────────────────── indicadores ─────────────────────────

    fun efficiency(a: Area): Double = game.secretarios[a.ordinal]?.let { 0.85 + 0.075 * it.competence } ?: 0.9

    /** Pessoas atendidas pelas obras prontas de uma área (toda a jurisdição ou uma região). */
    fun coverage(a: Area, bairro: Int = -1): Double = game.obras
        .filter { it.done && (bairro < 0 || it.bairro == bairro) && Content.obra(it.type).area == a }
        .sumOf { obraServes(Content.obra(it.type)) }

    fun spendEffect(a: Area) = 26 * tanh(1.6 * (game.spend[a.ordinal] * efficiency(a) - 1))
    fun obraEffect(a: Area) = 22 * (1 - exp(-coverage(a) / game.pop * 1.2))

    fun lawEffect(a: Area) = game.laws.sumOf { Laws.def(game, it).ind[a] ?: 0.0 }

    fun target(a: Area): Double {
        val i = a.ordinal
        return (game.base[i] + spendEffect(a) + obraEffect(a) + game.legacy[i] + lawEffect(a) + mod("ind_${a.name}")).coerceIn(2.0, 98.0)
    }

    /** Satisfação geral com os serviços (0..100); guardada uma vez por dia. */
    fun satisfaction(): Double = Area.entries.sumOf { it.weight * game.ind[it.ordinal] } / Area.entries.sumOf { it.weight }
    private var sat = satisfaction()

    /** Termos que puxam a aprovação do governo numa região. */
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
        // prefeito aliado ajuda o governador no município
        if (level == Level.ESTADO && governs && bs.mayorRel > 20) s += 3
        return min(s, 14.0)
    }

    private var lawAprov = 0.0

    fun approvalParts(b: Int): ApprovalParts {
        val bs = game.bairros[b]
        val inTerm = game.day - game.termStart
        val taxes = if (level == Level.CIDADE) -(game.iptu - 1) * 14 - (game.iss - 3) * 2.5 else -(game.iptu - 1) * 10 - (game.iss - 18) * 1.5
        return ApprovalParts(
            // o eleitor cobra mais do governo estadual do que da prefeitura
            services = sat - (if (level == Level.CIDADE) 2 else 3),
            local = localBonus(b),
            need = (game.ind[bs.need.ordinal] - 50) * 0.15,
            taxes = taxes + mod("aprov") + lawAprov,
            honeymoon = if (governs) 6 * max(0.0, 1 - inTerm / 180.0) else 0.0,
            late = if (game.late) -12.0 else 0.0,
            bump = if (governs) bs.bump else 0.0,
        )
    }

    /** Fama da carreira: cargos já exercidos e como saiu deles. */
    val fame: Double get() = (game.career.history.sumOf { (it.approval - 40) * 0.15 } + game.career.history.size * 2.0).coerceIn(-5.0, 18.0)

    /** Para onde vai o seu apoio numa região quando você não governa (parlamentar ou candidato). */
    fun supportTarget(b: Int): Double {
        val bs = game.bairros[b]
        val stance = when (game.stance) {
            1 -> (game.execApproval - 50) * 0.25
            -1 -> (50 - game.execApproval) * 0.3
            else -> 0.0
        }
        val mayor = if (level == Level.ESTADO && bs.mayorParty == game.player.party) 4.0 else 0.0
        return (12 + bs.local + game.career.capital * 0.3 + fame + stance + party.size * 6 + mayor + bs.rallies * 2.5).coerceIn(1.0, 95.0)
    }

    // ───────────────────────── passo diário ─────────────────────────

    fun advanceDay() {
        val g = game
        if (g.gameOver != null || g.gapPending) return
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
        sat = satisfaction()
        lawAprov = g.laws.sumOf { Laws.def(g, it).aprov }
        if (governs) {
            for (i in g.bairros.indices) {
                val bs = g.bairros[i]
                bs.approval += (approvalParts(i).total - bs.approval) * 0.006
                bs.bump *= 0.97
            }
            g.approval = g.bairros.sumOf { it.approval * it.popShare }
            g.execApproval = g.approval
        } else {
            // governo da IA: a aprovação dele e o seu apoio andam separados
            var exec = 0.0
            for (i in g.bairros.indices) {
                val bs = g.bairros[i]
                exec += approvalParts(i).total * bs.popShare
                bs.approval += (supportTarget(i) - bs.approval) * 0.006
                bs.bump *= 0.97
            }
            g.execApproval += (exec - g.execApproval) * 0.006
            g.approval = g.bairros.sumOf { it.approval * it.popShare }
        }
        g.modifiers.removeAll { it.until <= g.day }

        if (today.monthValue != before.monthValue) closeMonth(before)
        if (today.year != before.year) closeYear(before.year)
        Elections.tick(this)
        if (g.gameOver != null || g.gapPending || switchTo != null) return
        Camara.pending(this)?.let { if (g.day >= it.voteDay) Camara.vote(this, it) }
        Camara.execBill(this)?.let { if (g.day >= it.voteDay) Camara.vote(this, it) }
        val canSpawn = g.inOffice && g.election?.let { !it.finished } != true
        if (g.settings.randomEvents && canSpawn && g.events.isEmpty() && !g.candidacyPending && g.day >= g.nextEventDay) {
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
        if (governs) news("ribbon", "Inaugurado: ${t.name} ${Fmt.inB(b.name)}", 1, o.bairro)
        else news("ribbon", "O ${execTitle} inaugura: ${t.name} ${Fmt.inB(b.name)}", 0, o.bairro)
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
        if (governs) {
            if (g.late && !wasLate) news("alert", "Caixa negativo: salários atrasados. Corte gastos ou faça um empréstimo", 3)
            if (!g.late && wasLate) news("money", "Salários em dia de novo", 1)
        }

        // a jurisdição cresce mais quando vai bem
        val econ = g.ind[Area.ECONOMIA.ordinal]
        g.pop *= 1 + (0.006 + (g.execApproval - 50) * 0.00004 + (econ - 50) * 0.00008) / 12
        g.pib *= 1 + (0.012 + (econ - 50) * 0.0007 + (g.ind[Area.SEGURANCA.ordinal] - 50) * 0.0002 + mod("pib")) / 12

        if (governs) {
            for (s in g.secretarios) s?.let {
                it.monthsInOffice++
                // secretário desonesto pode virar escândalo
                if (g.settings.randomEvents && Crisis.ESCANDALO in g.settings.crises && rng(it.id).nextDouble() < (5 - it.honesty) * 0.006 && g.events.none { e -> e.key == "escandalo" })
                    g.events += PendingEvent("escandalo", g.day, area = it.area, text = it.name)
            }
        } else aiGovern()
        if (month.monthValue % 6 == 0) Setup.refreshPool(this)
        // promessas vencidas
        for (p in g.promises.filter { !it.kept && it.until <= g.day }) {
            g.bairros[p.bairro].approval -= 6
            g.camara.firstOrNull { it.id == p.vereador }?.let { it.favor -= 20; it.relation -= 20 }
            news("broken", "Promessa não cumprida ${Fmt.inB(g.bairros[p.bairro].name)}", 2, p.bairro)
        }
        g.promises.removeAll { it.kept || it.until <= g.day }

        Camara.monthly(this)
        Legislativo.monthly(this)
        if (level == Level.ESTADO) Mayors.monthly(this)

        val rec = MonthRecord(g.day, g.approval, g.cash, rev, exp, g.pop)
        g.history += rec
        g.lastMonth = rec
        if (g.history.size > 120) g.history.removeAt(0)
    }

    /** O governo da IA: preenche secretarias, faz obras quando sobra caixa e evita atrasar salário. */
    private fun aiGovern() {
        val g = game
        for (a in Area.entries) if (g.secretarios[a.ordinal] == null) g.pool.filter { it.area == a }.maxByOrNull { it.competence }?.let { hire(it.id, quiet = true) }
        val b = budget()
        if (g.cash > b.revenue * 0.06 && activeObras < maxObras) {
            val r = rng(990)
            val area = Area.entries.minBy { g.ind[it.ordinal] + r.nextDouble() * 10 }
            val t = obras.filter { it.area == area }.minByOrNull { it.cost } ?: obras.random(r)
            var x = r.nextDouble()
            val unit = g.bairros.indices.firstOrNull { x -= g.bairros[it].popShare; x <= 0 } ?: 0
            build(t.id, unit, quiet = true)
        }
        if (g.cash < 0) borrow(min(borrowLimit, -g.cash * 1.2), quiet = true)
    }

    private fun closeYear(year: Int) {
        val g = game
        val y = g.year
        if (governs && y.months >= 6 && y.taxBase > 0) {
            val health = y.health / y.taxBase
            val edu = y.education / y.taxBase
            val pers = y.personnel / y.revenue
            val fails = buildList {
                if (health < healthFloor) add("saúde abaixo de ${(healthFloor * 100).roundToInt()}%")
                if (edu < 0.25) add("educação abaixo de 25%")
                if (pers > personnelCap) add("pessoal acima de ${(personnelCap * 100).roundToInt()}%")
            }
            if (fails.isEmpty()) news("check", "Contas de $year aprovadas pelo Tribunal de Contas", 1)
            else {
                g.rejected++
                for (b in g.bairros) b.approval -= 4
                if (g.rejected >= 2) g.career.ineligibleUntil = year + 8
                news("gavel", "Contas de $year rejeitadas: ${fails.joinToString(", ")}" +
                    if (g.rejected >= 2) ". Você ficou inelegível até ${year + 8}" else ". Mais uma rejeição e você fica inelegível", 3)
            }
        }
        g.year = YearTotals()
        Legislativo.newYear(this)
    }

    // ───────────────────────── ações do jogador ─────────────────────────

    fun setSpend(a: Area, level: Double) { game.spend[a.ordinal] = level.coerceIn(0.3, 2.0) }
    fun setIptu(v: Double) { game.iptu = v.coerceIn(0.6, 1.6) }
    fun setIss(v: Double) { game.iss = v.coerceIn(taxBRange.start, taxBRange.endInclusive) }

    /** Inicia uma obra; devolve o motivo se não der. */
    fun build(typeId: String, bairro: Int, share: Double = 1.0, quiet: Boolean = false): String? {
        val t = Content.obra(typeId)
        if (activeObras >= maxObras && share >= 1.0) return "Limite de $maxObras obras ao mesmo tempo"
        val entrada = obraCost(t) * share * 0.15
        if (share >= 1.0 && game.cash < entrada) return "Falta caixa para a entrada (${Fmt.money(entrada)})"
        game.cash -= entrada
        game.obras += Obra(game.nextId++, t.id, bairro, game.day, paid = entrada, share = share)
        if (!quiet) news("build", "Obra iniciada: ${t.name} ${Fmt.inB(game.bairros[bairro].name)}", 0, bairro)
        return null
    }

    fun hire(id: Int, quiet: Boolean = false): String? {
        val s = game.pool.firstOrNull { it.id == id } ?: return "Candidato não encontrado"
        game.pool.remove(s)
        game.secretarios[s.area.ordinal] = s
        Setup.fillPool(this, s.area)
        if (!quiet) news("person", "${s.name} (${partyOf(s.party).sigla}) assume ${s.area.label}", 0)
        return null
    }

    fun fire(a: Area) {
        val s = game.secretarios[a.ordinal] ?: return
        game.secretarios[a.ordinal] = null
        news("person", "${s.name} deixa a secretaria de ${a.label}", 0)
    }

    val debtLimit get() = budget().revenue * (if (level == Level.CIDADE) 1.2 else 2.0)
    val borrowLimit get() = max(0.0, min(budget().revenue * 0.16 - game.year.borrowed, debtLimit - game.debt))

    fun borrow(amount: Double, quiet: Boolean = false): String? {
        if (amount <= 0) return null
        if (amount > borrowLimit + 1) return "Limite de crédito: ${Fmt.money(borrowLimit)} neste ano"
        game.debt += amount; game.cash += amount; game.year.borrowed += amount
        if (game.late && game.cash >= 0) game.late = false
        if (!quiet) news("bank", "Empréstimo de ${Fmt.money(amount)} contratado", 0)
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

    /** Muda a aprovação (ou o apoio) de uma região — ou de todas, com −1 — na hora. */
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

    /** Último ano do mandato atual (a eleição do cargo é nesse ano). */
    val termEndYear get() = dateOf(game.termStart).year + 3
    val electionDate: LocalDate get() = firstSunday(termEndYear, 10)
}
