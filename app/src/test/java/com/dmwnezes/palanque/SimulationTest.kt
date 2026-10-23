package com.dmwnezes.palanque

import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.sim.Area
import com.dmwnezes.palanque.sim.Content
import com.dmwnezes.palanque.sim.Elections
import com.dmwnezes.palanque.sim.Events
import com.dmwnezes.palanque.sim.Politician
import com.dmwnezes.palanque.sim.Settings
import com.dmwnezes.palanque.sim.Setup
import com.dmwnezes.palanque.sim.Sim
import com.dmwnezes.palanque.world.Brasil
import com.dmwnezes.palanque.world.CityPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

object TestBr {
    val br: Brasil by lazy { File("src/main/assets/brasil.bin").inputStream().use { Brasil.load(it) } }
    const val HORIZONTE = 2305233
    const val FORTALEZA = 2304400

    fun newSim(city: Int = HORIZONTE, seed: Long = 7, role: com.dmwnezes.palanque.sim.Role = com.dmwnezes.palanque.sim.Role.PREFEITO): Sim {
        val g = Setup.newGame(br, Settings(seed = seed), city, Politician("Daniel Menezes", "psd"), Content.PARTIES, role = role)
        return Sim(br, g)
    }

    /**
     * Joga sozinho: resolve eventos com a 1ª opção, faz campanha, constrói, manda emendas e vota.
     * pick escolhe o cargo nas eleições; gap decide o que fazer fora do cargo. Devolve o Sim final (pode ter trocado de jurisdição).
     */
    fun autoplay(start: Sim, days: Int, log: MutableList<String> = mutableListOf(), lazy: Boolean = false, pick: (Sim, List<com.dmwnezes.palanque.sim.Role>) -> com.dmwnezes.palanque.sim.Role?): Sim {
        var s = start
        var day = 0
        while (s.game.gameOver == null && day < days) {
            val g = s.game
            s.advanceDay(); day++
            s.switchTo?.let { ng -> log += "troca para ${ng.level} (${ng.running})"; s = Sim(br, ng) }
            val gg = s.game
            while (gg.events.isNotEmpty()) Events.resolve(s, 0)
            if (gg.candidacyPending) { val r = pick(s, Elections.options(s)); log += "${s.date.year}: disputa $r"; Elections.declare(s, r); continue }
            if (gg.gapPending) { log += "${s.date.year}: fora do cargo"; Elections.waitUntil(s, Elections.nextElections(s).first()); continue }
            gg.election?.let { e ->
                e.seen = true
                if (!e.conventionDone) Elections.confirmConvention(s, 0, Elections.coalitionOptions(s).filter { it.second }.take(2).map { it.first.id })
                while (e.debate > 0) Elections.answerDebate(s, if (lazy) 1 else 0)
                if (!e.finished && e.fund > Elections.adCost(s) && (!lazy || e.boost < 3)) Elections.advertise(s)
                if (!lazy && !e.finished && e.phase == 1) for (b in gg.bairros.indices.take(6)) Elections.rally(s, b)
                if (e.finished && e.round >= 1 && day % 30 == 0) Unit
            }
            if (s.governs && day % 60 == 0 && gg.cash > s.budget().revenue * 0.15) {
                val t = s.obras[(day / 60) % s.obras.size]
                s.build(t.id, (day / 60) % gg.bairros.size)
            }
            if (s.governs) {
                if (gg.late) s.borrow(s.borrowLimit)
                for (a in com.dmwnezes.palanque.sim.Area.entries) if (gg.secretarios[a.ordinal] == null) gg.pool.firstOrNull { it.area == a }?.let { s.hire(it.id) }
            }
            if (s.legislates) {
                com.dmwnezes.palanque.sim.Camara.execBill(s)?.let { com.dmwnezes.palanque.sim.Legislativo.vote(s, it, 1) }
                if (gg.emendasLeft > 0 && day % (if (lazy) 300 else 45) == 0) {
                    val unit = gg.bairros.indices.maxBy { gg.bairros[it].popShare }
                    com.dmwnezes.palanque.sim.Legislativo.sendEmenda(s, unit, com.dmwnezes.palanque.sim.Area.SAUDE, gg.emendasLeft / 3)
                }
                if (!lazy) com.dmwnezes.palanque.sim.Legislativo.speech(s)
            }
            check(!(gg.approval.isNaN() || gg.cash.isNaN() || gg.ind.any { it.isNaN() })) { "NaN em ${s.place.name} dia $day" }
        }
        return s
    }
}

class SimulationTest {
    @Test fun brasilLoads() {
        val br = TestBr.br
        assertEquals(27, br.states.size)
        assertTrue(br.cities.size > 5500)
        val h = br.city(TestBr.HORIZONTE)!!
        assertEquals("Horizonte", h.name)
        assertEquals("CE", h.sigla)
    }

    /** Toda cidade gera bairros válidos — testa uma amostra grande e as maiores. */
    @Test fun bairrosForManyCities() {
        val br = TestBr.br
        val sample = br.cities.filterIndexed { i, _ -> i % 37 == 0 } + br.cities.sortedByDescending { it.stat(com.dmwnezes.palanque.world.Stat.POP) }.take(30)
        for (c in sample) {
            val p = CityPlan.build(c)
            assertTrue("${c.name}: ${p.bairros.size} bairros", p.bairros.size >= 4)
            val area = p.bairros.sumOf { it.areaKm2.toDouble() }
            val real = p.outline.sumOf { kotlin.math.abs(com.dmwnezes.palanque.world.Geo.area(it)).toDouble() }
            assertEquals("${c.name}: bairros cobrem a cidade", real, area, real * 0.02)
            for (b in p.bairros) assertTrue("${c.name}: rótulo dentro", p.bairroAt(b.cx, b.cy) >= 0)
        }
    }

    @Test fun startIsBalanced() {
        val s = TestBr.newSim()
        val b = s.budget()
        assertTrue("receita ${b.revenue}", b.revenue > 1e8 && b.revenue < 1e9)
        assertTrue("equilíbrio ${b.balance / b.revenue}", kotlin.math.abs(b.balance / b.revenue) < 0.08)
        val l = s.limits()
        assertTrue(l.healthOk && l.educationOk && l.personnelOk)
        assertTrue("aprovação ${s.game.approval}", s.game.approval in 30.0..75.0)
        assertTrue(s.game.pool.size == Area.entries.size * 3)
    }

    /** Oito anos jogando no automático, resolvendo eventos e construindo: nada explode. */
    @Test fun eightYears() {
        for (city in listOf(TestBr.HORIZONTE, TestBr.FORTALEZA, 3550308, 5300108)) {
            val s = TestBr.newSim(city)
            val g = s.game
            for (a in Area.entries) g.pool.firstOrNull { it.area == a }?.let { s.hire(it.id) }
            val log = mutableListOf<String>()
            // sempre tenta a reeleição para prefeito; depois do 2º mandato, para de vez
            val end = TestBr.autoplay(s, 2900, log) { sim, opts -> com.dmwnezes.palanque.sim.Role.PREFEITO.takeIf { it in opts && sim.game.role == it } }
            assertTrue("${s.city.name}: obras", g.obras.count { it.done } > 3)
            println("${s.city.name}: ${end.game.role.label} · no cargo ${end.game.inOffice} · mandatos ${g.termNumber} · aprovação ${g.approval.toInt()} · ${log.joinToString("; ")}")
        }
    }

    @Test fun camara() {
        val s = TestBr.newSim()
        val g = s.game
        assertEquals(17, g.camara.size) // Horizonte: 80 a 120 mil habitantes
        assertEquals(9, com.dmwnezes.palanque.sim.Camara.seats(5000.0))
        assertEquals(55, com.dmwnezes.palanque.sim.Camara.seats(12e6))
        val C = com.dmwnezes.palanque.sim.Camara
        assertEquals(null, C.send(s, "escola_integral"))
        assertNotNull("só um projeto por vez", C.send(s, "tarifa_zero"))
        // negociar com todo mundo garante a maioria
        val bill = C.pending(s)!!
        for (v in g.camara) { C.talk(s, v, bill.id); C.cargo(s, v) }
        repeat(31) { s.advanceDay(); g.events.clear() }
        assertEquals("aprovado", g.bills.first().status)
        assertTrue("escola_integral" in g.laws)
        assertTrue(s.budget().laws > 0 && s.budget().cargos > 0)
        assertTrue(s.lawEffect(Area.EDUCACAO) > 0)
        // revogar
        assertEquals(null, C.send(s, "escola_integral", revoke = true))
        repeat(31) { s.advanceDay(); g.events.clear() }
        assertTrue(g.bills.last().status != "tramitando")
    }

    /** Carreira: vereador → prefeito (mesma cidade) e prefeito → governador (troca para o estado). */
    @Test fun career() {
        val v = TestBr.newSim(role = com.dmwnezes.palanque.sim.Role.VEREADOR)
        assertTrue(v.legislates && v.game.emendasLeft > 0 && v.game.execName.isNotEmpty())
        val log = mutableListOf<String>()
        var s = TestBr.autoplay(v, 365 * 4 + 10, log) { _, opts -> com.dmwnezes.palanque.sim.Role.PREFEITO.takeIf { it in opts } }
        println("vereador: " + log.joinToString("; ") + " → ${s.game.role.label}, no cargo ${s.game.inOffice}, apoio ${s.game.approval.toInt()}")
        assertTrue(log.any { it.contains("disputa PREFEITO") })
        // governador do Ceará, 8 anos
        val gov = TestBr.newSim(role = com.dmwnezes.palanque.sim.Role.GOVERNADOR)
        assertEquals(com.dmwnezes.palanque.sim.Level.ESTADO, gov.level)
        assertEquals(184, gov.game.bairros.size)
        assertEquals(46, gov.game.camara.size) // Assembleia do Ceará
        assertTrue("prefeitos", gov.game.bairros.all { it.mayorParty.isNotEmpty() })
        val b = gov.budget()
        assertTrue("receita do estado ${b.revenue}", b.revenue in 2e10..8e10)
        val log2 = mutableListOf<String>()
        s = TestBr.autoplay(gov, 365 * 8, log2) { sim, opts -> com.dmwnezes.palanque.sim.Role.GOVERNADOR.takeIf { it in opts && sim.governs } ?: com.dmwnezes.palanque.sim.Role.DEP_FEDERAL.takeIf { it in opts } }
        println("governador: " + log2.joinToString("; ") + " → ${s.game.role.label}, aprovação ${s.game.approval.toInt()}, obras ${s.game.obras.count { it.done }}")
        // deputado federal: casa de 513, eleição proporcional
        val dep = TestBr.newSim(role = com.dmwnezes.palanque.sim.Role.DEP_FEDERAL)
        assertEquals(513, dep.game.camara.size)
        val log3 = mutableListOf<String>()
        s = TestBr.autoplay(dep, 365 * 4 + 10, log3) { _, opts -> com.dmwnezes.palanque.sim.Role.DEP_FEDERAL.takeIf { it in opts } }
        println("deputado federal: " + log3.joinToString("; ") + " → no cargo ${s.game.inOffice}")
        // prefeito que larga a prefeitura em 2030 para disputar o governo
        val p = TestBr.newSim()
        val log4 = mutableListOf<String>()
        s = TestBr.autoplay(p, 365 * 2 + 210, log4) { _, opts -> com.dmwnezes.palanque.sim.Role.GOVERNADOR.takeIf { it in opts } }
        assertTrue(log4.joinToString(), log4.any { it.contains("troca para ESTADO") })
        assertEquals(com.dmwnezes.palanque.sim.Level.ESTADO, s.game.level)
        println("prefeito → governador: " + log4.joinToString("; ") + " → ${s.game.role.label}, no cargo ${s.game.inOffice}")
    }

    @Test fun campaign() {
        val s = TestBr.newSim()
        val g = s.game
        while (g.election == null) {
            s.advanceDay(); g.events.clear()
            if (g.candidacyPending) Elections.declare(s, if (Elections.isMunicipal(s.date.year)) com.dmwnezes.palanque.sim.Role.PREFEITO else null)
        }
        val e = g.election!!
        assertEquals(0, e.phase)
        assertEquals(3, e.viceOptions.size)
        val ok = Elections.coalitionOptions(s).filter { it.second }.take(2).map { it.first.id }
        val fund0 = e.fund
        Elections.confirmConvention(s, 1, ok)
        assertTrue(e.conventionDone && e.fund > fund0 && e.tv in 0.05..0.8)
        assertNotNull("comício antes da campanha", Elections.rally(s, 0))
        var debates = 0
        while (!e.finished) {
            s.advanceDay(); g.events.clear()
            if (e.debate > 0) { debates++; while (e.debate > 0) Elections.answerDebate(s, 1) }
        }
        assertTrue("debates $debates", debates >= 2)
        assertTrue(e.polls.size >= 6 && e.pollBairro.size == g.bairros.size)
        assertTrue("promessas", g.campaignPromises.isNotEmpty())
    }

    @Test fun randomEventsOff() {
        val s = TestBr.newSim()
        s.game.settings.randomEvents = false
        repeat(1200) { s.advanceDay(); assertTrue("evento ${s.game.events.firstOrNull()?.key}", s.game.events.all { it.key == "eleicao_geral" || it.key == "cassacao" }); s.game.events.clear() }
    }

    @Test fun saveRoundTrip() {
        val s = TestBr.newSim()
        repeat(400) { s.advanceDay(); s.game.events.clear() }
        val store = SaveStore(Files.createTempDirectory("sv").toFile())
        store.saveText("1", store.encode(s.game), com.dmwnezes.palanque.data.SaveMeta("1", s.city.name, s.game.day, 0, "Prefeito"))
        val back = store.load("1")!!
        assertEquals(s.game.day, back.day)
        assertEquals(s.game.cash, back.cash, 1e-6)
        val s2 = Sim(TestBr.br, back)
        repeat(30) { s2.advanceDay(); s2.game.events.clear() }
        assertEquals("Horizonte", store.meta("1")!!.city)
    }
}
