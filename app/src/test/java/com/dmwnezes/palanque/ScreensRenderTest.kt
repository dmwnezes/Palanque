package com.dmwnezes.palanque

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.sim.Area
import com.dmwnezes.palanque.sim.Elections
import com.dmwnezes.palanque.sim.PendingEvent
import com.dmwnezes.palanque.ui.GameController
import com.dmwnezes.palanque.ui.GameScreen
import com.dmwnezes.palanque.ui.HomeScreen
import com.dmwnezes.palanque.ui.Layer
import com.dmwnezes.palanque.ui.NewGameFlow
import com.dmwnezes.palanque.ui.PalanqueTheme
import com.dmwnezes.palanque.ui.Tab
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** Gera capturas das telas em app/build/screens (o GitHub guarda como artefato). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi", sdk = [34])
class ScreensRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/screens").also { it.mkdirs() }
    private fun store() = SaveStore(Files.createTempDirectory("s").toFile())

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { HomeScreen(TestBr.br, store(), {}, {}) } }
        shot("01_inicio")
    }

    @Test fun newGame() {
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { NewGameFlow(TestBr.br, {}) { _, _, _, _, _ -> } } }
        shot("02_novo_jogo")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Daniel Menezes")
        rule.onNodeWithText("Continuar").performClick()
        shot("03_cargo")
        rule.onNodeWithText("Continuar").performClick()
        shot("04_estado")
        rule.onNodeWithText("Continuar").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("horiz")
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Horizonte").performClick()
        shot("05_cidade")
        rule.onNodeWithText("Continuar").performClick()
        shot("06_ajustes")
    }

    @Test fun game() {
        val sim = TestBr.newSim()
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("10_mapa")
        rule.runOnIdle {
            for (a in Area.entries.take(5)) sim.game.pool.firstOrNull { it.area == a }?.let { sim.hire(it.id) }
            sim.build("ubs", 0); sim.build("escola", 1); sim.build("praca", 2)
            repeat(320) { sim.advanceDay(); sim.game.events.clear() }
            sim.build("base_gm", 0)
            com.dmwnezes.palanque.sim.Camara.send(sim, "tarifa_zero")
            repeat(31) { sim.advanceDay(); sim.game.events.clear() }
            com.dmwnezes.palanque.sim.Camara.send(sim, "alvara_na_hora")
            c.select(0)
        }
        shot("11_bairro")
        for ((i, t) in Tab.entries.withIndex()) {
            val subs = when (t) { Tab.GOVERNO, Tab.OBRAS, Tab.CAMARA -> 3; Tab.CIDADE -> 4; Tab.CARREIRA -> 2; else -> 1 }
            for (k in 0 until subs) {
                rule.runOnIdle { c.select(-1); c.tab = t; c.subTab[t] = k; c.refresh() }
                shot("2${i}${k}_${t.name.lowercase()}")
            }
        }
        rule.runOnIdle { c.tab = null; c.showSettings = true; c.refresh() }
        shot("29_menu")
        rule.runOnIdle { c.showSettings = false; c.layer = Layer.POPULACAO; c.refresh() }
        shot("30_camada_populacao")
        rule.runOnIdle { c.layer = Layer.OBRAS; c.refresh() }
        shot("31_camada_obras")
        rule.runOnIdle { c.layer = Layer.APROVACAO; sim.game.events += PendingEvent("temporal", sim.game.day, bairro = 1, amount = 900_000.0); c.refresh() }
        shot("40_evento")
        rule.runOnIdle {
            sim.game.events.clear()
            while (sim.game.election == null) {
                sim.advanceDay(); sim.game.events.clear()
                if (sim.game.candidacyPending) com.dmwnezes.palanque.sim.Elections.declare(sim, if (com.dmwnezes.palanque.sim.Elections.isMunicipal(sim.date.year)) com.dmwnezes.palanque.sim.Role.PREFEITO else null)
            }
            c.refresh()
        }
        shot("41_convencao")
        rule.runOnIdle {
            Elections.confirmConvention(sim, 0, Elections.coalitionOptions(sim).filter { it.second }.take(2).map { it.first.id })
            while (sim.game.election?.debate == 0) { sim.advanceDay(); sim.game.events.clear() }
            c.refresh()
        }
        shot("44_debate")
        rule.runOnIdle {
            while (sim.game.election!!.debate > 0) Elections.answerDebate(sim, 1)
            Elections.rally(sim, 0); Elections.advertise(sim)
            c.tab = Tab.CARREIRA; c.subTab[Tab.CARREIRA] = 1; c.refresh()
        }
        shot("45_campanha")
        rule.runOnIdle { c.tab = null; c.layer = Layer.VOTOS; c.refresh() }
        shot("46_intencao")
        rule.runOnIdle {
            c.tab = null
            while (sim.game.election?.let { it.round >= 1 } != true) {
                sim.advanceDay(); sim.game.events.clear()
                sim.game.election?.let { e -> while (e.debate > 0) Elections.answerDebate(sim, 0) }
            }
            c.refresh()
        }
        shot("42_resultado")
        rule.runOnIdle { sim.game.election?.seen = true; c.layer = Layer.VOTOS; c.refresh() }
        shot("43_mapa_votos")
    }

    /** Governador do Ceará: mapa do estado, municípios, orçamento estadual, Assembleia. */
    @Test fun governor() {
        val sim = TestBr.newSim(role = com.dmwnezes.palanque.sim.Role.GOVERNADOR)
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("60_governador")
        rule.runOnIdle {
            sim.build("hosp_regional", sim.game.bairros.indices.maxBy { sim.game.bairros[it].popShare })
            repeat(40) { sim.advanceDay(); sim.game.events.clear() }
            c.select(sim.game.bairros.indices.maxBy { sim.game.bairros[it].popShare })
        }
        shot("61_municipio")
        for ((t, k) in listOf(Tab.GOVERNO to 0, Tab.GOVERNO to 1, Tab.OBRAS to 0, Tab.CAMARA to 0)) {
            rule.runOnIdle { c.select(-1); c.tab = t; c.subTab[t] = k; c.refresh() }
            shot("6${2 + listOf(Tab.GOVERNO to 0, Tab.GOVERNO to 1, Tab.OBRAS to 0, Tab.CAMARA to 0).indexOf(t to k)}_${t.name.lowercase()}$k")
        }
    }

    /** Mudar um imposto com a aba aberta atualiza os números na hora (sem fechar e reabrir). */
    @Test fun sheetUpdatesInPlace() {
        val sim = TestBr.newSim()
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        rule.runOnIdle { c.tab = Tab.GOVERNO; c.subTab[Tab.GOVERNO] = 1; c.refresh() }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { sim.setIptu(1.5); c.refresh() }
        rule.mainClock.advanceTimeBy(1000)
        val want = com.dmwnezes.palanque.sim.Fmt.money(sim.budget().iptu)
        rule.onAllNodesWithText(want).fetchSemanticsNodes().let { org.junit.Assert.assertTrue("não achou $want", it.isNotEmpty()) }
    }

    /** Presidente: mapa do Brasil, orçamento da União, Congresso, mídia e bastidores. */
    @Test fun president() {
        val sim = TestBr.newSim(TestBr.FORTALEZA, role = com.dmwnezes.palanque.sim.Role.PRESIDENTE)
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("90_presidente")
        rule.runOnIdle {
            sim.build("universidade", 0)
            repeat(40) { sim.advanceDay(); sim.game.events.clear() }
            c.select(sim.game.bairros.indices.maxBy { sim.game.bairros[it].popShare })
        }
        shot("91_estado")
        val tabs = listOf(Tab.GOVERNO to 0, Tab.GOVERNO to 1, Tab.OBRAS to 0, Tab.EQUIPE to 0, Tab.CARREIRA to 2, Tab.CARREIRA to 3)
        for ((i, tk) in tabs.withIndex()) {
            rule.runOnIdle { c.select(-1); c.tab = tk.first; c.subTab[tk.first] = tk.second; c.refresh() }
            shot("9${2 + i}_${tk.first.name.lowercase()}${tk.second}")
        }
    }

    /** Vereador e deputado: abas do mandato legislativo, candidatura e fora do cargo. */
    @Test fun legislator() {
        val sim = TestBr.newSim(role = com.dmwnezes.palanque.sim.Role.VEREADOR)
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("70_vereador")
        rule.runOnIdle {
            while (com.dmwnezes.palanque.sim.Camara.execBill(sim) == null) { sim.advanceDay(); sim.game.events.clear() }
            com.dmwnezes.palanque.sim.Legislativo.sendEmenda(sim, 0, com.dmwnezes.palanque.sim.Area.SAUDE, sim.game.emendasLeft / 3)
            c.tab = Tab.CAMARA; c.subTab[Tab.CAMARA] = 1; c.refresh()
        }
        shot("71_votacao")
        rule.runOnIdle { c.tab = Tab.EMENDAS; c.emendaTarget = 2; c.refresh() }
        shot("72_emendas")
        rule.runOnIdle { c.tab = Tab.MANDATO; c.refresh() }
        shot("73_mandato")
        rule.runOnIdle {
            c.tab = null
            while (!sim.game.candidacyPending) { sim.advanceDay(); sim.game.events.clear() }
            c.refresh()
        }
        shot("74_candidatura")
        rule.runOnIdle { com.dmwnezes.palanque.sim.Elections.declare(sim, null); sim.game.gapPending = true; c.refresh() }
        shot("75_fora_do_cargo")
    }

    @Test fun bigCity() {
        val sim = TestBr.newSim(TestBr.FORTALEZA)
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("50_fortaleza")
    }
}
