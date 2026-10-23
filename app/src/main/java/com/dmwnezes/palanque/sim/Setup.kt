package com.dmwnezes.palanque.sim

import com.dmwnezes.palanque.world.Brasil
import com.dmwnezes.palanque.world.CityPlan
import com.dmwnezes.palanque.world.Region
import com.dmwnezes.palanque.world.Stat
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Cria um jogo novo a partir dos dados reais da cidade escolhida. */
object Setup {

    /** Ponto de partida de cada área, a partir dos indicadores reais (IBGE, PNUD, Ipea). */
    fun baseIndicators(c: Region, r: Random): DoubleArray {
        val idh = c.stat(Stat.IDH, 0.65f).toDouble()
        val alfab = c.stat(Stat.ALFAB, 88f).toDouble()
        val agua = c.stat(Stat.AGUA, 70f).toDouble()
        val esgoto = c.stat(Stat.ESGOTO, 40f).toDouble()
        val homic = c.stat(Stat.HOMIC, 25f).toDouble()
        val pc = c.stat(Stat.PIB_PC, 25000f).toDouble()
        val renda = c.stat(Stat.RENDA, 900f).toDouble()
        val pop = c.stat(Stat.POP, 20000f).toDouble()
        val cap = if (c.capital) 1.0 else 0.0
        fun j(s: Double) = (r.nextDouble() - 0.5) * 2 * s
        val v = DoubleArray(Area.entries.size)
        v[Area.SAUDE.ordinal] = 25 + (idh - 0.5) * 110 + agua * 0.1 + j(3.0)
        v[Area.EDUCACAO.ordinal] = (alfab - 70) * 2 + (idh - 0.5) * 60 + j(3.0)
        v[Area.SEGURANCA.ordinal] = 85 - homic * 0.9 + j(3.0)
        v[Area.ECONOMIA.ordinal] = 20 + 12 * ln(pc / 10000) / ln(2.0) + renda / 100 + j(3.0)
        v[Area.TRANSPORTE.ordinal] = 40 + cap * 5 + j(8.0)
        v[Area.SANEAMENTO.ordinal] = 0.4 * agua + 0.6 * esgoto + j(2.0)
        v[Area.CULTURA.ordinal] = 30 + (idh - 0.5) * 60 + cap * 10 + kotlin.math.log10(pop) * 2 + j(5.0)
        v[Area.AMBIENTE.ordinal] = 40 + (esgoto - 50) * 0.2 + j(8.0)
        v[Area.TURISMO.ordinal] = 25 + cap * 15 + r.nextDouble() * 15
        for (i in v.indices) v[i] = v[i].coerceIn(10.0, 88.0)
        return v
    }

    /** Primeiro dia de um cargo começado do zero: municipais em 2029, estaduais e federais em 2031. */
    fun startDay(role: Role) = if (role.municipal) 0 else java.time.temporal.ChronoUnit.DAYS.between(START_DATE, java.time.LocalDate.of(2031, 1, 1)).toInt()

    /**
     * Jogo novo na jurisdição do cargo (a cidade ou o estado da cidade natal), com os dados reais.
     * inOffice = false cria um candidato sem mandato (troca de nível no meio da carreira).
     */
    fun newGame(
        br: Brasil, settings: Settings, cityCode: Int, player: Politician, parties: List<Party>,
        role: Role = Role.PREFEITO, career: Career? = null, day: Int? = null, inOffice: Boolean = true, running: Role? = null,
        feed: List<FeedItem> = emptyList(),
    ): Game {
        val city = br.city(cityCode) ?: error("cidade $cityCode")
        val level = role.level
        val uf = cityCode / 100000
        val region = when (level) { Level.CIDADE -> city; Level.ESTADO -> br.state(uf); Level.PAIS -> br.country }
        val r = Random(settings.seed * 31 + cityCode + role.ordinal * 7)
        val pop = region.stat(Stat.POP, 20000f).toDouble()
        val pib = region.stat(Stat.PIB).takeIf { !it.isNaN() }?.toDouble() ?: (pop * 25000)
        val needs = Area.entries.filter { it.weight >= 0.06 }

        val bairros: MutableList<BairroState>
        var home = 0
        if (level == Level.CIDADE) {
            val plan = CityPlan.build(city)
            // população por bairro: mais densa perto do Centro
            val c0 = plan.bairros[0]
            val area = plan.bairros.sumOf { it.areaKm2.toDouble() }
            val reach = sqrt(area) * 0.25
            val dists = plan.bairros.map { hypot((it.cx - c0.cx).toDouble(), (it.cy - c0.cy).toDouble()) }
            val maxD = dists.max().coerceAtLeast(1e-3)
            val w = plan.bairros.mapIndexed { i, b -> b.areaKm2 * (exp(-dists[i] / reach) + 0.04) * (if (i == 0) 1.6 else 1.0) * (0.8 + r.nextDouble() * 0.4) }
            val total = w.sum()
            val rural = plan.bairros.mapIndexed { i, b -> i > 0 && area > 150 && dists[i] > maxD * 0.55 && w[i] / total < 0.06 && b.areaKm2 > area * 0.08 }
            val names = Content.bairroNames(r, plan.bairros.size, rural)
            bairros = plan.bairros.indices.map { i -> BairroState(names[i], w[i] / total, 50.0, needs.random(r)) }.toMutableList()
            home = r.nextInt(bairros.size)
        } else if (level == Level.PAIS) {
            // os 27 estados, na ordem de Brasil.states (a mesma do mapa)
            val tot = br.states.sumOf { it.stat(Stat.POP, 1000f).toDouble() }
            bairros = br.states.map { BairroState(it.name, it.stat(Stat.POP, 1000f) / tot, 50.0, needs.random(r)) }.toMutableList()
            home = br.states.indexOfFirst { it.code == uf }.coerceAtLeast(0)
        } else {
            val cities = br.citiesOf(uf)
            val tot = cities.sumOf { it.stat(Stat.POP, 1000f).toDouble() }
            bairros = cities.map { BairroState(it.name, it.stat(Stat.POP, 1000f) / tot, 50.0, needs.random(r)) }.toMutableList()
            home = cities.indexOfFirst { it.code == cityCode }.coerceAtLeast(0)
        }

        val base = baseIndicators(region, r)
        val d0 = day ?: startDay(role)
        val g = Game(
            settings = settings, cityCode = cityCode, player = player, parties = parties.toMutableList(),
            role = role, day = d0, bairros = bairros, base = base, ind = base.copyOf(), pop = pop, pib = pib,
            termStart = d0, level = level, uf = uf, career = career ?: Career(cityCode), inOffice = inOffice, running = running,
        )
        g.feed += feed
        g.pibPc0 = pib / pop
        val sim = Sim(br, g)
        // alíquota padrão do imposto B (ISS, ICMS modal ou contribuições federais)
        g.iss = sim.taxBDefault
        // escala de custo: no nível 1 o governo gasta 96% do que arrecada
        val b0 = sim.budget()
        g.costScale = ((0.96 * b0.revenue - b0.camara) / (pop * (Area.refTotal(level) + sim.adminPc))).coerceIn(0.4, 3.0)
        val b1 = sim.budget()
        g.cash = b1.revenue * 0.05
        g.debt = b1.revenue * (if (level == Level.CIDADE) 0.08 else 0.4)
        if (level != Level.CIDADE) Mayors.init(sim)
        val execApproval = g.bairros.indices.sumOf { sim.approvalParts(it).total * g.bairros[it].popShare }
        g.execApproval = execApproval
        if (role.executive && inOffice) {
            for (i in g.bairros.indices) g.bairros[i].approval = (sim.approvalParts(i).total + (r.nextDouble() - 0.5) * 8).coerceIn(15.0, 85.0)
        } else {
            // o governo é da IA: um nome de outro partido (às vezes do seu)
            val ps = g.parties.filter { !it.custom }
            g.execParty = if (r.nextDouble() < 0.3) player.party else ps.filter { it.id != player.party }.maxBy { it.size * r.nextDouble() }.id
            g.execName = Content.personName(r)
            if (g.execParty == player.party) g.stance = 1
            g.bairros[home].local = if (inOffice) 25.0 else 15.0
            for (i in g.bairros.indices) g.bairros[i].approval = (sim.supportTarget(i) + (r.nextDouble() - 0.5) * 6).coerceIn(5.0, 90.0)
        }
        g.approval = g.bairros.sumOf { it.approval * it.popShare }
        refreshPool(sim)
        Camara.elect(sim)
        if (role.executive && inOffice) {
            g.secretarios.indices.forEach { g.secretarios[it] = null }
        } else {
            // o governo da IA já começa com secretariado
            for (a in Area.entries) g.pool.filter { it.area == a }.maxByOrNull { it.competence }?.let { sim.hire(it.id, quiet = true) }
        }
        if (inOffice && !role.executive) g.emendasLeft = Legislativo.emendaBudget(sim)
        val where = when (level) {
            Level.CIDADE -> city.name
            Level.ESTADO -> Fmt.ofState(uf, region.name).removePrefix("do ").removePrefix("da ").removePrefix("de ")
            Level.PAIS -> "Brasil"
        }
        sim.news("flag", if (inOffice) "Posse: ${player.name}, ${role.label.lowercase()} ${if (level == Level.CIDADE) "de" else "—"} $where"
            else "${player.name} se lança candidato a ${running?.label?.lowercase()} ${if (level == Level.CIDADE) "em" else "—"} $where", 1)
        return g
    }

    /** Três candidatos por secretaria vaga. */
    fun refreshPool(sim: Sim) {
        sim.game.pool.clear()
        for (a in Area.entries) fillPool(sim, a)
    }

    fun fillPool(sim: Sim, a: Area) {
        val g = sim.game
        val r = sim.rng(a.ordinal * 101 + g.nextId)
        val parties = g.parties.filter { !it.custom }
        while (g.pool.count { it.area == a } < 3) {
            // metade vem do seu partido ou de aliados próximos
            val near = parties.sortedBy { kotlin.math.abs(it.econ - sim.party.econ) + kotlin.math.abs(it.social - sim.party.social) }
            val party = if (r.nextDouble() < 0.35) sim.party else (if (r.nextBoolean()) near.take(6) else parties).let { ps ->
                val tot = ps.sumOf { it.size.toDouble() }
                var x = r.nextDouble() * tot
                ps.firstOrNull { x -= it.size; x <= 0 } ?: ps.last()
            }
            val comp = listOf(1, 2, 2, 3, 3, 3, 4, 4, 5).random(r)
            g.pool += Secretario(g.nextId++, Content.personName(r), a, party.id, comp, (1..5).random(r), (1..5).random(r))
        }
    }
}
