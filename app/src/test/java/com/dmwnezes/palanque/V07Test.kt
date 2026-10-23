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
