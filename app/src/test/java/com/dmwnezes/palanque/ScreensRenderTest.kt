package com.dmwnezes.palanque

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
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
        rule.setContent { PalanqueTheme { NewGameFlow(TestBr.br, {}) { _, _, _, _ -> } } }
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
            c.select(0)
        }
        shot("11_bairro")
        for ((i, t) in Tab.entries.withIndex()) {
            val subs = when (t) { Tab.GOVERNO, Tab.OBRAS -> 3; Tab.CIDADE -> 4; Tab.CARREIRA -> 2; else -> 1 }
            for (k in 0 until subs) {
                rule.runOnIdle { c.select(-1); c.tab = t; c.subTab[t] = k; c.refresh() }
                shot("2${i}${k}_${t.name.lowercase()}")
            }
        }
        rule.runOnIdle { c.tab = null; c.layer = Layer.POPULACAO; c.refresh() }
        shot("30_camada_populacao")
        rule.runOnIdle { c.layer = Layer.OBRAS; c.refresh() }
        shot("31_camada_obras")
        rule.runOnIdle { c.layer = Layer.APROVACAO; sim.game.events += PendingEvent("temporal", sim.game.day, bairro = 1, amount = 900_000.0); c.refresh() }
        shot("40_evento")
        rule.runOnIdle {
            sim.game.events.clear()
            while (sim.game.election == null) { sim.advanceDay(); sim.game.events.clear() }
            repeat(30) { sim.advanceDay(); sim.game.events.clear() }
            Elections.rally(sim, 0); Elections.advertise(sim)
            c.tab = Tab.CARREIRA; c.subTab[Tab.CARREIRA] = 1; c.refresh()
        }
        shot("41_campanha")
        rule.runOnIdle {
            c.tab = null
            while (sim.game.election?.let { it.round >= 1 } != true) { sim.advanceDay(); sim.game.events.clear() }
            c.refresh()
        }
        shot("42_resultado")
        rule.runOnIdle { sim.game.election?.seen = true; c.layer = Layer.VOTOS; c.refresh() }
        shot("43_mapa_votos")
    }

    @Test fun bigCity() {
        val sim = TestBr.newSim(TestBr.FORTALEZA)
        val c = GameController(sim, store(), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { PalanqueTheme { GameScreen(c, store(), {}, {}) } }
        shot("50_fortaleza")
    }
}
