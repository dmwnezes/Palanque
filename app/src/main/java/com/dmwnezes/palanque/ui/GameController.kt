package com.dmwnezes.palanque.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dmwnezes.palanque.data.SaveMeta
import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.sim.FeedItem
import com.dmwnezes.palanque.sim.Sim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Tab(val label: String) { GOVERNO("Governo"), OBRAS("Obras"), EQUIPE("Equipe"), CIDADE("Cidade"), CARREIRA("Carreira") }

enum class Layer(val label: String) { APROVACAO("Aprovação"), POPULACAO("População"), OBRAS("Obras"), VOTOS("Votos") }

class Toast(val id: Long, val icon: String, val text: String, val tone: Int, val bairro: Int, val born: Long)

/** Estado da tela de jogo: relógio, seleção, gavetas e avisos. */
class GameController(val sim: Sim, private val store: SaveStore, private val scope: CoroutineScope) {
    /** Muda a cada dia ou ação: a interface relê os números. */
    var tick by mutableLongStateOf(0L)
        private set
    var paused by mutableStateOf(true)
    var speed by mutableIntStateOf(2)
    var tab by mutableStateOf<Tab?>(null)
    val subTab = mutableStateMapOf<Tab, Int>()
    var selBairro by mutableIntStateOf(-1)
    var layer by mutableStateOf(Layer.APROVACAO)
    var showSettings by mutableStateOf(false)
    /** obra escolhida no catálogo, esperando o toque num bairro */
    var placing by mutableStateOf<String?>(null)
    /** bairro escolhido antes de abrir o catálogo ("Construir aqui") */
    var buildTarget by mutableIntStateOf(-1)
    val camera = MapCamera()
    val toasts = mutableStateListOf<Toast>()
    private var toastId = 0L
    private var acc = 0.0
    private var lastMonth = sim.date.monthValue

    val game get() = sim.game

    var feedback: Feedback? = null
    private var hadEvent = false
    private var lastTickMs = 0L

    init { sim.listener = { onFeed(it) } }

    fun refresh() { tick++ }

    /** Segundos reais por dia de jogo em cada velocidade. */
    private val dayLength = doubleArrayOf(1.0, 0.45, 0.2, 0.08)

    val electionPending get() = game.election?.let { it.round >= 1 && !it.seen } == true
    val blocked get() = game.events.isNotEmpty() || game.gameOver != null || electionPending

    fun onFrame(dtSec: Double, nowMs: Long) {
        if (toasts.isNotEmpty()) toasts.removeAll { nowMs - it.born > 3800 }
        val ev = game.events.isNotEmpty() || electionPending
        if (ev && !hadEvent) feedback?.play(if (electionPending) Sfx.VOTE else Sfx.EVENT)
        hadEvent = ev
        if (paused || blocked || saving) { acc = 0.0; return }
        acc += dtSec.coerceAtMost(0.25)
        val len = dayLength[(speed - 1).coerceIn(0, 3)]
        var days = 0
        val start = System.nanoTime()
        while (acc >= len && days < 3 && !blocked && !saving && System.nanoTime() - start < 12_000_000L) {
            sim.advanceDay()
            acc -= len
            days++
            val m = sim.date.monthValue
            if (m != lastMonth) { lastMonth = m; if (m % 3 == 1) autosave() }
        }
        if (acc > len * 3) acc = len * 3
        if (days > 0 && nowMs - lastTickMs >= 100) { tick++; lastTickMs = nowMs }
        if (blocked) { paused = true; tick++ }
    }

    fun togglePause() { paused = !paused; tick++ }
    fun setSpeedLevel(v: Int) { speed = v.coerceIn(1, 4); paused = false; tick++ }

    fun say(text: String, tone: Int = 0, icon: String = "alert", bairro: Int = -1) {
        toasts.add(0, Toast(++toastId, icon, text, tone, bairro, System.currentTimeMillis()))
        while (toasts.size > 4) toasts.removeAt(toasts.lastIndex)
    }

    private fun onFeed(f: FeedItem) {
        say(f.text, f.tone, f.icon, f.bairro)
        when (f.icon) {
            "ribbon" -> feedback?.play(Sfx.RIBBON)
            "build" -> feedback?.play(Sfx.BUILD)
            "trophy" -> feedback?.play(Sfx.VICTORY)
            "defeat" -> feedback?.play(Sfx.DEFEAT)
        }
    }

    /** Executa uma ação do jogador; mostra o motivo se ela for recusada. */
    fun act(okText: String? = null, block: () -> String?) {
        val why = block()
        if (why != null) say(why, 2) else if (okText != null) say(okText, 1, "check")
        tick++
    }

    fun select(b: Int) { selBairro = b; tick++ }
    fun openTab(t: Tab?) { tab = if (tab == t) null else t; placing = null; tick++ }

    /** Começa uma obra escolhida no catálogo no bairro tocado. */
    fun place(b: Int) {
        val t = placing ?: return
        act { sim.build(t, b) }
        placing = null; selBairro = b; buildTarget = -1
    }

    fun autosave() = save("auto")

    @Volatile var saving = false
        private set

    private fun meta() = SaveMeta("", sim.city.name, game.day, 0, game.role.label, game.player.name)

    /** Grava em segundo plano; o relógio espera terminar para não haver mudanças no meio. */
    fun save(slot: String) {
        if (saving) return
        saving = true
        val meta = meta()
        scope.launch(Dispatchers.Default) {
            try { store.saveText(slot, store.encode(game), meta) } catch (_: Throwable) {} finally { saving = false }
        }
    }

    /** Gravação imediata (ao sair do app). */
    fun saveNow(slot: String = "auto") {
        runCatching { store.saveText(slot, store.encode(game), meta()) }
    }
}
