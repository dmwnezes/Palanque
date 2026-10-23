package com.dmwnezes.palanque.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.data.SaveStore
import com.dmwnezes.palanque.sim.Content
import com.dmwnezes.palanque.sim.Events
import com.dmwnezes.palanque.sim.Fmt
import kotlin.math.ln

@Composable
fun GameScreen(c: GameController, store: SaveStore, onExit: () -> Unit, onLoad: (String) -> Unit) {
    var frame by remember { mutableLongStateOf(0L) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember(c) { c.feedback = Feedback(ctx); 0 }

    LaunchedEffect(c) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) c.onFrame((t - last) / 1e9, System.currentTimeMillis())
            last = t
            frame = c.tick
        }
    }

    BackHandler {
        when {
            c.placing != null -> { c.placing = null; c.refresh() }
            c.tab != null -> c.openTab(null)
            c.selBairro >= 0 -> c.select(-1)
            else -> { c.paused = true; c.showSettings = true }
        }
    }

    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val sim = c.sim

    // valores da camada do mapa
    val maxDens = remember(c.tick, c.layer) { g.bairros.indices.maxOf { g.bairros[it].popShare / sim.plan.bairros[it].areaKm2 } }
    val paints: (Int) -> BairroPaint = { i ->
        val b = g.bairros[i]
        when (c.layer) {
            // contraste reforçado: 30% já é vermelho forte, 70% verde forte
            Layer.APROVACAO -> BairroPaint(Ink.heat((50 + (b.approval - 50) * 2.5).toFloat()).copy(alpha = 0.8f), "${b.approval.toInt()}%")
            Layer.POPULACAO -> {
                val d = (b.popShare / sim.plan.bairros[i].areaKm2) / maxDens
                val t = (1 + ln(d.coerceAtLeast(0.001)) / 6.9).coerceIn(0.05, 1.0).toFloat()
                BairroPaint(Ink.lerp(Ink.land, Ink.teal, t), Fmt.people(g.pop * b.popShare))
            }
            Layer.OBRAS -> {
                val v = (sim.localBonus(i) / 14).toFloat()
                BairroPaint(Ink.lerp(Ink.land, Ink.gold, 0.12f + v * 0.75f), g.obras.count { it.bairro == i }.takeIf { it > 0 }?.let { if (it == 1) "1 obra" else "$it obras" })
            }
            Layer.VOTOS -> {
                val e = g.election
                val res = e?.result?.getOrNull(i)
                if (res == null) BairroPaint(Ink.land, null) else {
                    val pl = e.candidates.indexOfFirst { it.player }
                    val mine = res[pl]
                    val best = res.filterIndexed { k, _ -> k != pl }.max()
                    BairroPaint(Ink.heat((50 + (mine - best) * 150).toFloat()).copy(alpha = 0.8f), Fmt.pct(mine))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CityMapView(c, Modifier.fillMaxSize(), paints, { frame }) { b ->
            when {
                c.placing != null && b >= 0 -> c.place(b)
                else -> c.select(if (b == c.selBairro) -1 else b)
            }
        }
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Ink.oceanDeep.copy(alpha = 0.9f), Color.Transparent))))

        Column(Modifier.fillMaxSize()) {
            TopBar(c)
            Toasts(c)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    IconBtn(Icons.Rounded.CenterFocusStrong, bg = Ink.panel, size = 44.dp) { c.camera.scale = 0f; c.refresh() }
                    Spacer(Modifier.height(8.dp))
                    LayerButton(c)
                }
            }
            c.placing?.let { PlacingBanner(c, it) }
            AnimatedVisibility(c.tab == null && c.placing == null && c.selBairro >= 0, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                if (c.selBairro >= 0) BairroCard(c, c.selBairro)
            }
            Dock(c)
        }

        AnimatedVisibility(c.tab != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().height(maxHeight * 0.7f).clip(Shapes.sheet).background(Ink.panelSolid)) {
                    Column(Modifier.fillMaxWidth().clickable { c.openTab(null) }.padding(top = 8.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(Shapes.chip).background(Ink.line))
                    }
                    Box(Modifier.weight(1f)) {
                        when (c.tab) {
                            Tab.GOVERNO -> GovernoSheet(c)
                            Tab.OBRAS -> ObrasSheet(c)
                            Tab.CAMARA -> CamaraSheet(c)
                            Tab.EQUIPE -> EquipeSheet(c)
                            Tab.CIDADE -> CidadeSheet(c)
                            Tab.CARREIRA -> CarreiraSheet(c)
                            null -> {}
                        }
                    }
                    Dock(c, inSheet = true)
                }
            }
        }

        g.events.firstOrNull()?.let { EventCard(c) }
        if (g.events.isEmpty() && c.electionPending) ElectionCard(c)

        g.gameOver?.let { go ->
            Overlay({}, dismissable = false) {
                val good = go.kind == "fim"
                Icon(if (good) Icons.Rounded.EmojiEvents else Icons.Rounded.HowToVote, null, tint = if (good) Ink.gold else Ink.coral,
                    modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text(go.title, style = Type.title, color = Ink.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(go.text, style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Row(Modifier.padding(vertical = 8.dp)) {
                    Cell("Aprovação final", "${g.approval.toInt()}%", Ink.scale(g.approval.toFloat()))
                    Cell("Obras entregues", "${g.obras.count { it.done }}")
                    Cell("Mandatos", "${g.termNumber}")
                }
                Spacer(Modifier.height(8.dp))
                Btn("Menu inicial", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { onExit() }
            }
        }

        if (c.showSettings) SettingsOverlay(c, store, onExit, onLoad)
    }
}

// ───────────────────────── barra superior ─────────────────────────

@Composable
private fun TopBar(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val d = c.sim.date
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(Shapes.chip).clickable { c.openTab(Tab.CIDADE) }.padding(4.dp)) {
                Text(c.sim.city.name, style = Type.heading.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.date(d)} · ${c.sim.city.sigla}", style = Type.small.copy(fontFeatureSettings = "tnum"), color = Ink.muted)
            }
            SpeedControl(c)
            Spacer(Modifier.width(8.dp))
            IconBtn(Icons.Rounded.Menu, size = 38.dp, bg = Ink.panel) { c.paused = true; c.showSettings = true }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val low = g.cash < c.sim.budget().revenue * 0.02
            StatPill(Icons.Rounded.Payments, Fmt.money(g.cash), if (g.cash < 0) Ink.coral else if (low) Ink.amber else Ink.text) { c.subTab[Tab.GOVERNO] = 0; c.openTab(Tab.GOVERNO) }
            StatPill(Icons.Rounded.ThumbUp, "${g.approval.toInt()}%", Ink.scale(g.approval.toFloat())) { c.subTab[Tab.CIDADE] = 0; c.openTab(Tab.CIDADE) }
            StatPill(Icons.Rounded.Groups, Fmt.people(g.pop), Ink.text) { c.subTab[Tab.CIDADE] = 1; c.openTab(Tab.CIDADE) }
            g.election?.takeIf { !it.finished }?.let { e ->
                val pl = e.candidates.indexOfFirst { it.player }
                val last = e.polls.lastOrNull()
                StatPill(Icons.Rounded.HowToVote, last?.let { Fmt.pct(it[pl]) } ?: "Campanha", Ink.gold) { c.openTab(Tab.CARREIRA) }
            }
        }
    }
}

@Composable
private fun SpeedControl(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    Row(Modifier.clip(Shapes.chip).background(Ink.panel).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (c.paused) Ink.gold else Ink.raised).clickable { c.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (c.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (c.paused) "Continuar" else "Pausar",
                tint = if (c.paused) Ink.onGold else Ink.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        for (s in 1..4) {
            val on = s <= c.speed
            Box(Modifier.size(width = 15.dp, height = 34.dp).clickable { c.setSpeedLevel(s) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 5.dp, height = (8 + s * 4).dp).clip(Shapes.chip).background(if (on && !c.paused) Ink.gold else if (on) Ink.muted else Ink.line))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun Toasts(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.End) {
        for (t in c.toasts.take(3)) {
            Row(
                Modifier.padding(top = 6.dp).widthIn(max = 320.dp).clip(Shapes.chip).background(Ink.panel)
                    .clickable { if (t.bairro >= 0) c.select(t.bairro); c.toasts.remove(t) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.of(t.icon), null, tint = Ink.tone(t.tone), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.text, style = Type.label, color = Ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LayerButton(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
        if (open) {
            Column(Modifier.clip(Shapes.card).background(Ink.panel).padding(6.dp)) {
                for (m in Layer.entries) {
                    if (m == Layer.VOTOS && c.game.election?.result.isNullOrEmpty()) continue
                    val sel = m == c.layer
                    Text(
                        m.label, style = Type.label, color = if (sel) Ink.onGold else Ink.text,
                        modifier = Modifier.padding(2.dp).clip(Shapes.chip).background(if (sel) Ink.gold else Color.Transparent)
                            .clickable { c.layer = m; open = false; c.refresh() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        IconBtn(Icons.Rounded.Layers, bg = Ink.panel, tint = Ink.text, size = 44.dp) { open = !open }
    }
}

// ───────────────────────── barra de abas ─────────────────────────

@Composable
fun Dock(c: GameController, inSheet: Boolean = false) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val icons = mapOf(
        Tab.GOVERNO to Icons.Rounded.AccountBalance,
        Tab.OBRAS to Icons.Rounded.Construction,
        Tab.CAMARA to Icons.Rounded.Gavel,
        Tab.EQUIPE to Icons.Rounded.Badge,
        Tab.CIDADE to Icons.Rounded.LocationCity,
        Tab.CARREIRA to Icons.Rounded.HowToVote,
    )
    Row(
        Modifier.fillMaxWidth().let { if (inSheet) it.background(Ink.oceanDeep) else it.padding(horizontal = 10.dp).clip(Shapes.card).background(Ink.panel) }
            .navigationBarsPadding().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (t in Tab.entries) {
            val sel = c.tab == t
            val badge = when (t) {
                Tab.EQUIPE -> c.game.secretarios.count { it == null }
                Tab.CAMARA -> if (c.game.bills.any { it.status == "tramitando" }) 1 else 0
                Tab.CARREIRA -> if (c.game.election?.let { !it.finished } == true) 1 else 0
                else -> 0
            }
            Column(Modifier.weight(1f).clip(Shapes.tile).clickable { c.openTab(t) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Box(Modifier.size(width = 52.dp, height = 30.dp).clip(Shapes.chip).background(if (sel) Ink.gold else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(icons[t]!!, t.label, tint = if (sel) Ink.onGold else Ink.muted, modifier = Modifier.size(22.dp))
                    }
                    if (badge > 0) Box(
                        Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(Ink.coral), contentAlignment = Alignment.Center,
                    ) { Text("$badge", style = Type.small.copy(fontSize = 10.sp), color = Color.White) }
                }
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = Type.small.copy(fontSize = 11.sp), color = if (sel) Ink.text else Ink.muted, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── cartões sobre o mapa ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BairroCard(c: GameController, b: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val bs = g.bairros[b]
    val parts = c.sim.approvalParts(b)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bs.name, style = Type.heading, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Preocupação: ${bs.need.label.lowercase()}", style = Type.small, color = Ink.muted)
            }
            IconBtn(Icons.Rounded.Close, size = 32.dp) { c.select(-1) }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Cell("Aprovação", "${bs.approval.toInt()}%", Ink.scale(bs.approval.toFloat()))
            Cell("Moradores", Fmt.people(g.pop * bs.popShare))
            Cell("Serviços aqui", Fmt.signed(parts.local), if (parts.local > 0.5) Ink.good else Ink.muted)
        }
        val here = g.obras.filter { it.bairro == b }
        if (here.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (o in here) {
                    val t = Content.obra(o.type)
                    StatPill(GameIcons.of(t.icon), if (o.done) t.name else "${t.name} ${(o.progress * 100).toInt()}%", if (o.done) Ink.gold else Ink.muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Construir aqui", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.Construction) {
                c.buildTarget = b; c.subTab[Tab.OBRAS] = 0; c.openTab(Tab.OBRAS)
            }
            val e = g.election
            if (e != null && !e.finished) Btn("Comício", Modifier.weight(1f), icon = Icons.Rounded.Campaign) {
                c.act("Comício ${Fmt.inB(bs.name)}") { com.dmwnezes.palanque.sim.Elections.rally(c.sim, b) }
            }
        }
    }
}

@Composable
private fun PlacingBanner(c: GameController, type: String) {
    val t = Content.obra(type)
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GameIcons.of(t.icon), null, tint = Ink.gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Toque no bairro", style = Type.heading.copy(fontSize = 16.sp), color = Ink.text)
            Text("${t.name} · ${Fmt.money(c.sim.obraCost(t))}", style = Type.small, color = Ink.muted)
        }
        Btn("Cancelar", kind = BtnKind.GHOST) { c.placing = null; c.refresh() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val e = c.game.events.firstOrNull() ?: return
    val d = Events.def(e.key)
    Overlay({}, dismissable = false) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(Ink.raised).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Icon(GameIcons.of(d.icon), null, tint = if (d.crisis != null) Ink.coral else Ink.gold, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(d.title(c.sim, e), style = Type.title.copy(fontSize = 22.sp), color = Ink.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(d.text(c.sim, e), style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.options.forEachIndexed { i, o ->
                val ok = o.enabled(c.sim, e)
                Column(
                    Modifier.fillMaxWidth().clip(Shapes.tile).background(if (ok) Ink.raised else Ink.oceanDeep)
                        .clickable(enabled = ok) { Events.resolve(c.sim, i); c.refresh() }.padding(12.dp),
                ) {
                    Text(o.label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (ok) Ink.text else Ink.faint)
                    val chips = o.chips(c.sim, e)
                    if (chips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (ch in chips) EffectChip(ch.text, ch.good)
                        }
                    }
                    if (!ok) Text("Precisa de um distrito industrial pronto", style = Type.small, color = Ink.coral)
                }
            }
        }
    }
}

@Composable
private fun ElectionCard(c: GameController) {
    val e = c.game.election ?: return
    val pl = e.candidates.indexOfFirst { it.player }
    Overlay({}, dismissable = false) {
        val title = when {
            e.finished && e.won -> "Vitória!"
            e.finished -> "Derrota"
            else -> "Vai ter segundo turno"
        }
        Icon(Icons.Rounded.HowToVote, null, tint = if (e.finished && !e.won) Ink.coral else Ink.gold, modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
        Text(title, style = Type.title, color = Ink.text, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        Text(if (e.round == 2) "Resultado do 2º turno" else "Resultado do 1º turno", style = Type.label, color = Ink.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        val rows = if (e.round == 2) listOf(pl, e.rival2).map { it to e.runoff[it] } else e.candidates.indices.map { it to e.totals[it] }
        for ((i, v) in rows.sortedByDescending { it.second }) {
            val cand = e.candidates[i]
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cand.name + if (cand.player) " (você)" else "", style = Type.body.copy(fontWeight = if (cand.player) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (cand.player) Ink.gold else Ink.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.sim.partyOf(cand.party).sigla, style = Type.small, color = Ink.muted)
                    Spacer(Modifier.width(10.dp))
                    Text(Fmt.pct(v, 1), style = Type.number, color = Ink.text)
                }
                Meter((v * 100).toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp), if (cand.player) Ink.gold else Ink.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                e.finished && e.won -> "Mais quatro anos. O segundo mandato começa em 1º de janeiro."
                e.finished -> "Você governa até 31 de dezembro."
                else -> "Mais quatro semanas de campanha contra ${e.candidates[e.rival2].name}."
            },
            style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Btn("Ver o mapa de votos", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { e.seen = true; c.layer = Layer.VOTOS; c.refresh() }
    }
}

@Composable
private fun SettingsOverlay(c: GameController, store: SaveStore, onExit: () -> Unit, onLoad: (String) -> Unit) {
    var confirmSlot by remember { mutableStateOf<String?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    val fb = c.feedback
    var sound by remember { mutableStateOf(fb?.sound ?: true) }
    var vib by remember { mutableStateOf(fb?.vibration ?: true) }
    Overlay({ c.showSettings = false }) {
        Text("Pausa", style = Type.title, color = Ink.text)
        Spacer(Modifier.height(8.dp))
        Text("Salvar", style = Type.label, color = Ink.muted)
        Spacer(Modifier.height(6.dp))
        for (slot in SaveStore.SLOTS) {
            val m = store.meta(slot)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Espaço $slot", style = Type.label, color = Ink.text)
                    Text(m?.let { "${it.city} · ${it.dateLabel()}" } ?: "Vazio", style = Type.small, color = Ink.muted)
                }
                Text("Salvar", style = Type.label, color = Ink.gold, modifier = Modifier.clip(Shapes.chip).clickable {
                    if (m != null) confirmSlot = slot else { c.save(slot); c.say("Jogo salvo", 1, "check"); c.showSettings = false }
                }.padding(horizontal = 10.dp, vertical = 6.dp))
                if (m != null) Text("Carregar", style = Type.label, color = Ink.teal, modifier = Modifier.clip(Shapes.chip).clickable { onLoad(slot) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Som", sound, Icons.AutoMirrored.Rounded.VolumeUp) { sound = it; fb?.sound = it }
        ToggleRow("Vibração", vib, Icons.Rounded.Vibration) { vib = it; fb?.vibration = it }
        var events by remember { mutableStateOf(c.game.settings.randomEvents) }
        ToggleRow("Eventos aleatórios", events, Icons.Rounded.Casino, sub = "Crises, pedidos e notícias que pedem decisão") {
            events = it; c.game.settings.randomEvents = it; c.refresh()
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Atualização", Modifier.weight(1f), icon = Icons.Rounded.SystemUpdate) { showUpdate = true }
            Btn("Sair", Modifier.weight(1f), BtnKind.GHOST, Icons.AutoMirrored.Rounded.Logout) { c.saveNow(); onExit() }
        }
        Spacer(Modifier.height(8.dp))
        Btn("Continuar", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { c.showSettings = false }
    }
    confirmSlot?.let { slot ->
        Overlay({ confirmSlot = null }) {
            Text("Substituir o espaço $slot?", style = Type.heading, color = Ink.text)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { confirmSlot = null }
                Btn("Substituir", Modifier.weight(1f), BtnKind.PRIMARY) { c.save(slot); c.say("Jogo salvo", 1, "check"); confirmSlot = null; c.showSettings = false }
            }
        }
    }
    if (showUpdate) com.dmwnezes.palanque.update.UpdateDialog { showUpdate = false }
}
