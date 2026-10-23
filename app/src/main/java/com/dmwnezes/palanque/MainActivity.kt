package com.dmwnezes.palanque

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.sim.Game
import com.dmwnezes.palanque.sim.Setup
import com.dmwnezes.palanque.sim.Sim
import com.dmwnezes.palanque.ui.GameController
import com.dmwnezes.palanque.ui.GameScreen
import com.dmwnezes.palanque.ui.HomeScreen
import com.dmwnezes.palanque.ui.Ink
import com.dmwnezes.palanque.ui.NewGameFlow
import com.dmwnezes.palanque.ui.PalanqueTheme
import com.dmwnezes.palanque.ui.Type
import com.dmwnezes.palanque.world.Brasil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val store = SaveStore(File(filesDir, "saves"))
        setContent { PalanqueTheme { App(store) } }
    }
}

private enum class Screen { LOADING, HOME, NEW_GAME, PLAYING }

/** O mapa do Brasil é lido uma vez por processo. */
private object BrHolder { var br: Brasil? = null }

@Composable
private fun App(store: SaveStore) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf(Screen.LOADING) }
    var controller by remember { mutableStateOf<GameController?>(null) }
    var homeKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { com.dmwnezes.palanque.update.UpdateState.checkOnStart(ctx) } }
    LaunchedEffect(Unit) {
        if (BrHolder.br == null) withContext(Dispatchers.Default) { BrHolder.br = ctx.assets.open("brasil.bin").use { Brasil.load(it) } }
        screen = if (controller != null) Screen.PLAYING else Screen.HOME
    }

    // salva ao sair do app
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, controller) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) controller?.let { if (it.game.gameOver == null) it.saveNow(); it.paused = true }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun start(game: Game) {
        controller = GameController(Sim(BrHolder.br!!, game), store, scope)
        screen = Screen.PLAYING
    }

    Box(Modifier.fillMaxSize().background(Ink.ocean)) {
        when (screen) {
            Screen.LOADING -> Text("Abrindo o mapa do Brasil…", style = Type.heading, color = Ink.muted, modifier = Modifier.align(Alignment.Center))
            Screen.HOME -> androidx.compose.runtime.key(homeKey) {
                HomeScreen(BrHolder.br!!, store, onNew = { screen = Screen.NEW_GAME }, onLoad = { slot -> store.load(slot)?.let { start(it) } })
            }
            Screen.NEW_GAME -> {
                BackHandler { screen = Screen.HOME }
                NewGameFlow(BrHolder.br!!, onBack = { screen = Screen.HOME }) { settings, city, player, parties, role ->
                    start(Setup.newGame(BrHolder.br!!, settings, city, player, parties, role = role))
                    controller?.autosave()
                }
            }
            Screen.PLAYING -> controller?.let { c ->
                GameScreen(
                    c, store,
                    onExit = { if (c.game.gameOver == null) c.saveNow(); controller = null; homeKey++; screen = Screen.HOME },
                    onLoad = { slot -> store.load(slot)?.let { start(it) } },
                    onSwitch = { ng -> start(ng); controller?.autosave() },
                )
            }
        }
    }
}
