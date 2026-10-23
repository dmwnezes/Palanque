package com.dmwnezes.palanque.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.sim.Area
import com.dmwnezes.palanque.sim.Camara
import com.dmwnezes.palanque.sim.Chip
import com.dmwnezes.palanque.sim.CustomLaw
import com.dmwnezes.palanque.sim.Fmt
import com.dmwnezes.palanque.sim.LawDef
import com.dmwnezes.palanque.sim.Laws
import com.dmwnezes.palanque.sim.Sim
import com.dmwnezes.palanque.sim.Vereador
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun stanceColor(st: Int) = when (st) { 1 -> Ink.good; -1 -> Ink.coral; else -> Ink.muted }
private fun stanceLabel(st: Int) = when (st) { 1 -> "Base"; -1 -> "Oposição"; else -> "Independente" }

/** Efeitos de uma lei como chips. */
fun lawChips(s: Sim, l: LawDef): List<Chip> = buildList {
    for ((a, v) in l.ind) add(Chip("${a.short} ${Fmt.signed(v)}", v >= 0))
    if (l.aprov != 0.0) add(Chip("${Fmt.signed(l.aprov)} aprovação", l.aprov > 0))
    if (l.costPc > 0) add(Chip("−${Fmt.money(s.game.pop * l.costPc * s.game.costScale).removePrefix("−")}/ano", false))
    if (l.iptuMul != 1.0) add(Chip("IPTU ${Fmt.signed((l.iptuMul - 1) * 100)}%", l.iptuMul > 1))
}

/** Plenário em semicírculo: cada bolinha é um vereador. */
@Composable
private fun Hemicycle(c: GameController) {
    val g = c.game
    val seats = g.camara.sortedBy { it.relation }
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = seats.size
        val rows = when { n <= 15 -> 2; n <= 29 -> 3; else -> 4 }
        val cx = size.width / 2; val cy = size.height - 6.dp.toPx()
        val rMax = minOf(size.width / 2 - 8.dp.toPx(), size.height - 14.dp.toPx())
        val rMin = rMax * 0.45f
        // cadeiras por fileira proporcionais ao raio
        val radii = List(rows) { i -> rMin + (rMax - rMin) * i / (rows - 1).coerceAtLeast(1) }
        val tot = radii.sum()
        val per = radii.map { (n * it / tot).roundToInt() }.toMutableList()
        per[rows - 1] += n - per.sum()
        val pos = ArrayList<Pair<Float, Offset>>()
        for (i in 0 until rows) for (k in 0 until per[i]) {
            val a = PI * (1 - (k + 0.5) / per[i])
            pos += a.toFloat() to Offset(cx + radii[i] * cos(a).toFloat(), cy - radii[i] * sin(a).toFloat())
        }
        // da esquerda (oposição) para a direita (base)
        val dot = minOf(9.dp.toPx(), rMax / (rows * 2.2f))
        pos.sortedByDescending { it.first }.forEachIndexed { i, (_, o) ->
            val v = seats.getOrNull(i) ?: return@forEachIndexed
            drawCircle(stanceColor(Camara.stance(v)), dot, o)
            if (v.party == g.player.party) drawCircle(Ink.gold, dot + 2.dp.toPx(), o, style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VereadorRow(c: GameController, v: Vereador, billKey: Int, expanded: Boolean, onToggle: () -> Unit, chance: Double? = null) {
    val s = c.sim
    val p = s.partyOf(v.party)
    val st = Camara.stance(v)
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).clickable(onClick = onToggle).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(stanceColor(st)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(p.sigla, Color(p.color))
                    Spacer(Modifier.width(6.dp))
                    Text("reduto: ${c.game.bairros[v.bairro].name}", style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (chance != null) {
                Text("${(chance * 100).roundToInt()}% sim", style = Type.label, color = if (chance >= 0.65) Ink.good else if (chance <= 0.35) Ink.coral else Ink.amber)
            } else Text(Fmt.signed(v.relation), style = Type.number.copy(fontSize = 14.sp), color = stanceColor(st))
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("Conversar", icon = Icons.Rounded.Forum, enabled = v.talked != billKey) { c.act("Conversa com ${v.name}") { Camara.talk(s, v, billKey) } }
                Btn("Emenda ${Fmt.money(Camara.emendaCost(s))}", icon = Icons.Rounded.Payments, enabled = v.funded != billKey) {
                    c.act("Emenda liberada para o reduto") { Camara.fund(s, v, billKey) }
                }
                Btn("Dar cargo", icon = Icons.Rounded.Badge, enabled = !v.hasCargo) { c.act("Cargo para indicado de ${v.name}") { Camara.cargo(s, v) } }
                Btn("Prometer obra", icon = Icons.Rounded.Construction, enabled = v.promised < c.game.day - 365) {
                    c.act("Obra prometida ${Fmt.inB(c.game.bairros[v.bairro].name)}") { Camara.promise(s, v) }
                }
            }
            Text("Conversar é de graça. Cargo custa ${Fmt.money(Camara.cargoCost(s))}/ano e pega mal na imprensa. Obra prometida não feita em 1 ano vira inimizade.",
                style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamaraSheet(c: GameController) {
    val s = c.sim
    val g = c.game
    var open by remember { mutableIntStateOf(-1) }
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val base = g.camara.count { Camara.stance(it) > 0 }
    val opp = g.camara.count { Camara.stance(it) < 0 }
    val bill = Camara.pending(s)
    val billKey = bill?.id ?: -(g.day / 30)
    Tabbed(c, Tab.CAMARA, "Câmara Municipal", "${g.camara.size} vereadores · maioria com ${g.camara.size / 2 + 1}", listOf("Plenário", "Projetos", "Leis")) { sel ->
        when (sel) {
            0 -> {
                Spacer(Modifier.height(6.dp))
                Hemicycle(c)
                Row(Modifier.padding(top = 6.dp)) {
                    Cell("Base", "$base", Ink.good)
                    Cell("Independentes", "${g.camara.size - base - opp}", Ink.muted)
                    Cell("Oposição", "$opp", Ink.coral)
                }
                Note("A relação melhora com aprovação alta, obras no reduto, secretários do partido do vereador e favores. Toque num vereador para negociar.")
                for (v in g.camara.sortedByDescending { it.relation }) VereadorRow(c, v, billKey, open == v.id, { open = if (open == v.id) -1 else v.id })
            }
            1 -> {
                Spacer(Modifier.height(6.dp))
                if (bill == null) {
                    Btn("Enviar projeto de lei", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Add) { picking = true }
                    Note("Um projeto por vez. A votação acontece 30 dias depois do envio; até lá dá para negociar.")
                } else {
                    val law = Laws.def(g, bill.law)
                    val cnt = Camara.count(s, bill)
                    Column(Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.raised).padding(14.dp)) {
                        Text(if (bill.revoke) "Revogar: ${law.name}" else law.name, style = Type.heading, color = Ink.text)
                        Text("Votação em ${bill.voteDay - g.day} dias", style = Type.small, color = Ink.muted)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Cell("A favor", "${cnt.yes}", Ink.good)
                            Cell("Indecisos", "${cnt.maybe}", Ink.amber)
                            Cell("Contra", "${cnt.no}", Ink.coral)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.chip)) {
                            if (cnt.yes > 0) Box(Modifier.weight(cnt.yes.toFloat()).height(8.dp).background(Ink.good))
                            if (cnt.maybe > 0) Box(Modifier.weight(cnt.maybe.toFloat()).height(8.dp).background(Ink.amber))
                            if (cnt.no > 0) Box(Modifier.weight(cnt.no.toFloat()).height(8.dp).background(Ink.coral))
                        }
                        Text("Precisa de ${g.camara.size / 2 + 1} votos", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(8.dp))
                        Btn("Retirar o projeto", kind = BtnKind.GHOST) { Camara.withdraw(s); c.refresh() }
                    }
                    SectionTitle("Quem negociar")
                    val list = g.camara.map { it to Camara.chance(s, it, law, bill.revoke) }.sortedBy { kotlin.math.abs(it.second - 0.5) }
                    for ((v, p) in list) VereadorRow(c, v, billKey, open == v.id, { open = if (open == v.id) -1 else v.id }, p)
                }
                val done = g.bills.filter { it.status != "tramitando" }.takeLast(12).reversed()
                if (done.isNotEmpty()) {
                    SectionTitle("Já votados")
                    for (b in done) {
                        val ok = b.status == "aprovado"
                        LineItem((if (b.revoke) "Revogar: " else "") + Laws.def(g, b.law).name,
                            if (b.status == "retirado") "retirado" else "${b.yes} a ${b.no}",
                            if (ok) Ink.good else if (b.status == "retirado") Ink.muted else Ink.coral,
                            sub = Fmt.date(s.dateOf(b.voteDay)))
                    }
                }
            }
            else -> {
                Spacer(Modifier.height(6.dp))
                if (g.laws.isEmpty()) Note("Nenhuma lei sua em vigor ainda. Envie projetos na aba Projetos.")
                for (id in g.laws) {
                    val l = Laws.def(g, id)
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
                        Text(l.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text)
                        Text(l.desc, style = Type.small, color = Ink.muted)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (ch in lawChips(s, l)) EffectChip(ch.text, ch.good)
                        }
                        Text("Revogar", style = Type.label, color = Ink.coral, modifier = Modifier.padding(top = 6.dp).clip(Shapes.chip)
                            .clickable { c.act("Projeto de revogação enviado") { Camara.send(s, id, revoke = true) } }.padding(vertical = 4.dp))
                    }
                }
                Note("Os efeitos chegam aos poucos, como o gasto nas áreas.")
            }
        }
    }
    if (picking) LawPicker(c, { picking = false }, { picking = false; creating = true })
    if (creating) LawEditor(c) { creating = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LawPicker(c: GameController, onDismiss: () -> Unit, onCreate: () -> Unit) {
    val s = c.sim
    val g = c.game
    Overlay(onDismiss) {
        Text("Projeto de lei", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        Spacer(Modifier.height(8.dp))
        Btn("Criar uma lei própria", Modifier.fillMaxWidth(), BtnKind.GHOST, Icons.Rounded.Edit) { onCreate() }
        Spacer(Modifier.height(8.dp))
        val laws = Laws.READY.filter { it.id !in g.laws } + g.customLaws.filter { it.id !in g.laws }.map { Laws.fromCustom(it) }
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            for (l in laws) {
                val cnt = Camara.count(s, l, false)
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                    .clickable { c.act("Projeto enviado à Câmara") { Camara.send(s, l.id) }; onDismiss() }.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text, modifier = Modifier.weight(1f))
                        Text("${cnt.yes}–${cnt.no}", style = Type.number.copy(fontSize = 14.sp), color = if (cnt.yes * 2 > g.camara.size) Ink.good else if (cnt.no * 2 > g.camara.size) Ink.coral else Ink.amber)
                    }
                    Text(l.desc, style = Type.small, color = Ink.muted)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (ch in lawChips(s, l)) EffectChip(ch.text, ch.good)
                    }
                }
            }
        }
        Text("O placar é a previsão hoje: a favor–contra.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LawEditor(c: GameController, onDone: () -> Unit) {
    val s = c.sim
    val g = c.game
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf(Area.SAUDE) }
    var intensity by remember { mutableIntStateOf(2) }
    var ideo by remember { mutableStateOf(0f) }
    var funding by remember { mutableIntStateOf(0) }
    Overlay(onDone) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            Text("Lei própria", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.raised).padding(14.dp)) {
                if (name.isEmpty()) Text("Nome da lei", style = Type.body, color = Ink.faint)
                BasicTextField(name, { name = it.take(48) }, singleLine = true, textStyle = Type.body.copy(color = Ink.text), cursorBrush = SolidColor(Ink.gold), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Text("Área", style = Type.label, color = Ink.muted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in Area.entries) {
                    val sel = a == area
                    Row(Modifier.clip(Shapes.chip).background(if (sel) Ink.gold else Ink.raised).clickable { area = a }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(GameIcons.area(a), null, tint = if (sel) Ink.onGold else Ink.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(a.short, style = Type.label, color = if (sel) Ink.onGold else Ink.text)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tamanho", style = Type.label, color = Ink.muted)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf(1, 2, 3), intensity, { Laws.INTENSITY[it - 1] }) { intensity = it }
            Spacer(Modifier.height(10.dp))
            Text("Quem paga", style = Type.label, color = Ink.muted)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf(0, 1), funding, { if (it == 0) "A prefeitura" else "Nova taxa" }) { funding = it }
            Spacer(Modifier.height(6.dp))
            SliderRow(Icons.Rounded.Balance, "Viés", ideo, -1f..1f, if (ideo < -0.3f) "Esquerda" else if (ideo > 0.3f) "Direita" else "Centro") { ideo = it }
            val preview = Laws.fromCustom(CustomLaw("preview", name.ifBlank { "Lei" }, area, intensity, ideo, funding))
            val cnt = Camara.count(s, preview, false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (ch in lawChips(s, preview)) EffectChip(ch.text, ch.good)
                EffectChip("Placar hoje ${cnt.yes}–${cnt.no}", cnt.yes * 2 > g.camara.size)
            }
            Spacer(Modifier.height(14.dp))
            Btn("Enviar à Câmara", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Gavel, enabled = name.isNotBlank()) {
                val law = CustomLaw("lei_${g.nextId++}", name.trim(), area, intensity, ideo, funding)
                g.customLaws += law
                c.act("Projeto enviado à Câmara") { Camara.send(s, law.id) }
                onDone()
            }
        }
    }
}
