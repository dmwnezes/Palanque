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

    fun newSim(city: Int = HORIZONTE, seed: Long = 7): Sim {
        val g = Setup.newGame(br, Settings(seed = seed), city, Politician("Daniel Menezes", "psd"), Content.PARTIES)
        return Sim(br, g)
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
            var day = 0
            while (g.gameOver == null && day < 3000) {
                s.advanceDay(); day++
                while (g.events.isNotEmpty()) Events.resolve(s, 0)
                g.election?.let { e ->
                    e.seen = true
                    if (!e.finished && e.fund > Elections.adCost(s)) Elections.advertise(s)
                }
                if (day % 60 == 0 && g.cash > s.budget().revenue * 0.15) {
                    val t = Content.OBRAS[(day / 60) % Content.OBRAS.size]
                    s.build(t.id, (day / 60) % g.bairros.size)
                }
                if (g.late) s.borrow(s.borrowLimit)
                assertFalse("NaN em ${s.city.name} dia $day", g.approval.isNaN() || g.cash.isNaN() || g.ind.any { it.isNaN() })
            }
            assertNotNull("${s.city.name}: jogo deveria acabar", g.gameOver)
            assertTrue(g.history.size > 40)
            assertTrue("${s.city.name}: obras", g.obras.count { it.done } > 3)
            println("${s.city.name}: ${g.gameOver!!.title} · mandatos ${g.termNumber} · aprovação ${g.approval.toInt()} · caixa ${g.cash.toLong()} · dívida ${g.debt.toLong()}")
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
