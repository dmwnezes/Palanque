package com.dmwnezes.palanque

import com.dmwnezes.palanque.sim.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Presidente, senador, mídia, esquemas e manobras. */
class V07Test {
    @Test fun presidentStartsWithinTheLaw() {
        val s = TestBr.newSim(TestBr.FORTALEZA, 3, Role.PRESIDENTE)
        val l = s.limits()
        assertTrue(l.healthOk && l.educationOk && l.personnelOk)
        assertEquals(27, s.game.bairros.size)
        assertEquals(81, s.game.camara.count { it.senator })
        assertEquals(594, s.game.camara.size)
        val end = TestBr.autoplay(s, 400, lazy = true) { _, _ -> null }
        assertTrue(end.game.approval in 20.0..90.0)
    }

    @Test fun senatorHasEightYears() {
        val s = TestBr.newSim(TestBr.FORTALEZA, 3, Role.SENADOR)
        assertEquals(2038, s.termEndYear)
        assertEquals(81, s.game.camara.size)
        assertTrue(Role.PRESIDENTE in Elections.options(s, 2034))
    }

    @Test fun presidencyNeedsExperience() {
        val s = TestBr.newSim(TestBr.HORIZONTE, 3, Role.VEREADOR)
        assertTrue(Role.PRESIDENTE !in Elections.options(s, 2030))
    }

    @Test fun schemesLeadToInvestigation() {
        val s = TestBr.newSim(TestBr.HORIZONTE, 5, Role.PREFEITO)
        var seen = false
        repeat(365 * 3) {
            if (s.game.gameOver != null) return@repeat
            s.advanceDay()
            if (Esquemas.canRun(s)) Esquemas.options(s).firstOrNull()?.let { Esquemas.run(s, it.id) }
            s.game.events.firstOrNull()?.let { if (it.key == "denuncia" || it.key == "operacao") seen = true; Events.resolve(s, 0) }
        }
        assertTrue(seen)
        assertTrue(s.game.caixa2 > 0 || s.game.gameOver != null)
    }

    @Test fun partySwitchOnlyInWindow() {
        val s = TestBr.newSim(TestBr.HORIZONTE, 5, Role.VEREADOR)
        assertTrue(Manobras.switchParty(s, "pt") != null) // janeiro de 2029: fechada
        s.game.day = s.dayOf(java.time.LocalDate.of(2030, 3, 10))
        assertEquals(null, Manobras.switchParty(s, "pt"))
        assertEquals("pt", s.game.player.party)
    }
}

class GolpeTest {
    @Test fun coupEndsElectionsOrEndsGame() {
        val s = TestBr.newSim(TestBr.FORTALEZA, 3, Role.PRESIDENTE)
        assertTrue(Golpe.block(TestBr.newSim(TestBr.HORIZONTE, 3, Role.PREFEITO)) != null)
        Golpe.coup(s)
        if (s.game.gameOver != null) return
        assertTrue(s.game.regime)
        val end = TestBr.autoplay(s, 365 * 10, lazy = true) { _, _ -> null }
        // nunca há eleição no regime; termina deposto, em transição, no exílio — ou ainda no poder
        assertTrue(end.game.election == null)
        assertTrue(end.game.regime)
    }
}

class CatalogTest {
    @Test fun idsAreUnique() {
        val obras = Content.OBRAS + Content.OBRAS_ESTADO + Content.OBRAS_PAIS
        assertEquals(obras.size, obras.map { it.id }.toSet().size)
        val laws = Laws.READY + Laws.ESTADO + Laws.FEDERAL
        assertEquals(laws.size, laws.map { it.id }.toSet().size)
    }

    @Test fun refineryHurtsEnvironment() {
        val s = TestBr.newSim(TestBr.FORTALEZA, 3, Role.PRESIDENTE)
        s.game.cash = 1e12
        val before = s.game.legacy[Area.AMBIENTE.ordinal]
        s.build("refinaria", 0)
        repeat(365 * 4 + 30) { s.advanceDay(); s.game.events.clear() }
        assertTrue(s.game.legacy[Area.AMBIENTE.ordinal] <= before - 2.0 + 1e-9 || s.game.obras.none { it.done && it.type == "refinaria" })
    }
}

class EconomyTest {
    /** Todo governo começa no azul e, sem mexer em nada, não quebra em quatro anos. */
    @Test fun startsBalancedAndStaysSolvent() {
        for (role in listOf(Role.PREFEITO, Role.GOVERNADOR, Role.PRESIDENTE)) {
            val s = TestBr.newSim(TestBr.FORTALEZA, 3, role)
            assertTrue("$role começa no vermelho", s.budget().balance > 0)
            repeat(365 * 4) { s.advanceDay(); s.game.events.clear() }
            assertTrue("$role quebrou", s.game.cash > 0)
        }
    }

    @Test fun strikeRaisesFadeWithInflation() {
        val s = TestBr.newSim(TestBr.HORIZONTE, 3, Role.PREFEITO)
        s.game.wage[Area.EDUCACAO.ordinal] = 1.08
        repeat(365 * 3) { s.advanceDay(); s.game.events.clear() }
        assertEquals(1.0, s.game.wage[Area.EDUCACAO.ordinal], 1e-9)
    }
}
