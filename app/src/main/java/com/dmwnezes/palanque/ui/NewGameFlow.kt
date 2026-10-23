package com.dmwnezes.palanque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.sim.Content
import com.dmwnezes.palanque.sim.Crisis
import com.dmwnezes.palanque.sim.Fmt
import com.dmwnezes.palanque.sim.Party
import com.dmwnezes.palanque.sim.Politician
import com.dmwnezes.palanque.sim.Role
import com.dmwnezes.palanque.sim.Settings
import com.dmwnezes.palanque.world.Brasil
import com.dmwnezes.palanque.world.Stat
import java.text.Normalizer

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

@Composable
private fun Field(value: String, placeholder: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Box(modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.raised).padding(horizontal = 14.dp, vertical = 14.dp)) {
        if (value.isEmpty()) Text(placeholder, style = Type.body, color = Ink.faint)
        BasicTextField(value, onChange, singleLine = true, textStyle = Type.body.copy(color = Ink.text), cursorBrush = SolidColor(Ink.gold), modifier = Modifier.fillMaxWidth())
    }
}

/** Novo jogo em cinco passos: você, cargo, estado, cidade e opções. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameFlow(br: Brasil, onBack: () -> Unit, onStart: (Settings, Int, Politician, List<Party>, Role) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val parties = remember { Content.PARTIES.toMutableList() }
    var party by remember { mutableStateOf("psd") }
    var creating by remember { mutableStateOf(false) }
    var uf by remember { mutableIntStateOf(23) }
    var city by remember { mutableIntStateOf(-1) }
    var query by remember { mutableStateOf("") }
    val crises = remember { Crisis.entries.toMutableSet() }
    var crisisTick by remember { mutableIntStateOf(0) }
    var randomEvents by remember { mutableStateOf(true) }
    var role by remember { mutableStateOf(Role.PREFEITO) }

    val titles = listOf("Quem é você", "Por onde começar", "Escolha o estado", "Sua cidade", "Últimos ajustes")
    val canNext = when (step) {
        0 -> name.isNotBlank()
        3 -> city >= 0
        else -> true
    }

    Column(Modifier.fillMaxSize().background(Ink.ocean).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack) { if (step == 0) onBack() else step-- }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Passo ${step + 1} de 5", style = Type.small, color = Ink.muted)
                Text(titles[step], style = Type.title, color = Ink.text)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..4) Box(Modifier.weight(1f).height(4.dp).clip(Shapes.chip).background(if (i <= step) Ink.gold else Ink.line))
        }
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (step) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Seu nome na urna", style = Type.label, color = Ink.muted)
                    Spacer(Modifier.height(6.dp))
                    Field(name, "Nome do político") { name = it.take(32) }
                    Spacer(Modifier.height(18.dp))
                    Text("Partido", style = Type.label, color = Ink.muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in parties) {
                            val sel = p.id == party
                            Row(
                                Modifier.clip(Shapes.chip).background(if (sel) Ink.gold else Ink.raised).clickable { party = p.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.color)))
                                Spacer(Modifier.width(6.dp))
                                Text("${p.sigla} ${p.number}", style = Type.label, color = if (sel) Ink.onGold else Ink.text)
                            }
                        }
                        Row(
                            Modifier.clip(Shapes.chip).border(1.dp, Ink.gold.copy(alpha = 0.6f), Shapes.chip).clickable { creating = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Ink.gold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Fundar partido", style = Type.label, color = Ink.gold)
                        }
                    }
                    val p = parties.first { it.id == party }
                    Spacer(Modifier.height(14.dp))
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text(p.name, style = Type.heading, color = Ink.text)
                            Spacer(Modifier.height(6.dp))
                            IdeologyBar("Economia", "Estado forte", "Mercado livre", p.econ)
                            IdeologyBar("Costumes", "Progressista", "Conservador", p.social)
                            if (p.custom) Text("Partido novo: sem tempo de TV nem fundo eleitoral grande.", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                1 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in Role.entries) {
                        val sel = r == role
                        Row(
                            Modifier.fillMaxWidth().clip(Shapes.tile).background(if (sel) Ink.gold.copy(alpha = 0.16f) else Ink.raised)
                                .let { if (sel) it.border(1.5.dp, Ink.gold, Shapes.tile) else it }.clickable(enabled = r.playable) { role = r }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (r.playable) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = if (sel) Ink.gold else Ink.faint, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.label, style = Type.heading.copy(fontSize = 17.sp), color = if (r.playable) Ink.text else Ink.muted)
                                Text(r.desc, style = Type.small, color = Ink.muted)
                            }
                            if (!r.playable) Text("em breve", style = Type.small, color = Ink.faint)
                        }
                    }
                    Text("Cargos municipais começam em 2029; estaduais e federais, em 2031. A cada eleição você escolhe o próximo degrau. Senador e presidente chegam nas próximas versões.",
                        style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))
                }
                2 -> Column {
                    RegionPicker(br.states, Modifier.fillMaxWidth().weight(1f), selected = uf, labels = true) { uf = it; city = -1; query = "" }
                    Spacer(Modifier.height(10.dp))
                    val s = br.state(uf)
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text(s.name, style = Type.heading, color = Ink.text)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Cell("População", Fmt.people(s.stat(Stat.POP).toDouble()))
                                Cell("Municípios", "${br.citiesOf(uf).size}")
                                Cell("IDH", "%.3f".format(s.stat(Stat.IDH)).replace('.', ','))
                            }
                        }
                    }
                }
                3 -> Column {
                    val all = remember(uf) { br.citiesOf(uf).sortedByDescending { it.stat(Stat.POP, 0f) } }
                    val q = fold(query.trim())
                    val list = remember(uf, q) { if (q.isEmpty()) all.take(60) else all.filter { fold(it.name).contains(q) }.take(60) }
                    RegionPicker(br.citiesOf(uf), Modifier.fillMaxWidth().height(150.dp), selected = city) { city = it }
                    Spacer(Modifier.height(8.dp))
                    Field(query, "Buscar entre ${all.size} cidades") { query = it }
                    Spacer(Modifier.height(8.dp))
                    br.city(city)?.let { c ->
                        Panel(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                            Column {
                                Text(c.name + if (c.capital) " · capital" else "", style = Type.heading.copy(fontSize = 17.sp), color = Ink.gold)
                                Row(Modifier.padding(top = 4.dp)) {
                                    Cell("População", Fmt.people(c.stat(Stat.POP, 0f).toDouble()))
                                    Cell("PIB/hab.", Fmt.money(c.stat(Stat.PIB_PC, 0f).toDouble()))
                                    Cell("IDH", c.stat(Stat.IDH).takeIf { !it.isNaN() }?.let { "%.3f".format(it).replace('.', ',') } ?: "—")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(list, key = { it.code }) { c ->
                            val sel = c.code == city
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Shapes.small).background(if (sel) Ink.gold.copy(alpha = 0.16f) else Color.Transparent)
                                    .clickable { city = c.code }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.name, style = Type.body.copy(fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal), color = if (sel) Ink.gold else Ink.text,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Fmt.people(c.stat(Stat.POP, 0f).toDouble()), style = Type.small, color = Ink.muted)
                            }
                        }
                    }
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    @Suppress("UNUSED_EXPRESSION") crisisTick
                    val c = br.city(city)!!
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Text("${name.trim()} · ${parties.first { it.id == party }.sigla}", style = Type.heading, color = Ink.text)
                            Text(if (role.level == com.dmwnezes.palanque.sim.Level.CIDADE) "${role.label} de ${c.name} (${c.sigla}) · posse em 1º de janeiro de 2029"
                                else "${role.label} ${Fmt.ofState(c.uf, br.state(c.uf).name)} · posse em 1º de janeiro de 2031 · cidade natal: ${c.name}",
                                style = Type.label, color = Ink.muted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ToggleRow("Eventos aleatórios", randomEvents, sub = "Dá para mudar depois no menu de pausa") { randomEvents = it }
                    Spacer(Modifier.height(8.dp))
                    Text("Crises que podem acontecer", style = Type.label, color = Ink.muted)
                    for (cr in Crisis.entries) ToggleRow(cr.label, cr in crises) { on -> if (on) crises += cr else crises -= cr; crisisTick++ }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Btn(if (step == 4) "Tomar posse" else "Continuar", Modifier.fillMaxWidth(), BtnKind.PRIMARY,
                if (step == 4) Icons.Rounded.Flag else null, enabled = canNext) {
                if (step < 4) step++
                else onStart(Settings(crises.toMutableSet(), System.currentTimeMillis() % 100000, randomEvents), city, Politician(name.trim(), party), parties, role)
            }
        }
    }

    if (creating) CreatePartyDialog(parties.map { it.number }.toSet(), { creating = false }) { p -> parties += p; party = p.id; creating = false }
}

@Composable
private fun IdeologyBar(label: String, left: String, right: String, v: Float) {
    val f = ((v + 1) / 2).coerceIn(0.04f, 1f)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = Type.small, color = Ink.muted)
        Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.chip).background(Ink.line))
            Box(Modifier.fillMaxWidth(f), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Ink.gold))
            }
        }
        Row {
            Text(left, style = Type.small.copy(fontSize = 11.sp), color = Ink.faint, modifier = Modifier.weight(1f))
            Text(right, style = Type.small.copy(fontSize = 11.sp), color = Ink.faint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreatePartyDialog(taken: Set<Int>, onDismiss: () -> Unit, onCreate: (Party) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sigla by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Content.PARTY_COLORS[0]) }
    var econ by remember { mutableStateOf(0f) }
    var social by remember { mutableStateOf(0f) }
    val number = remember { (10..99).first { it !in taken } }
    Overlay(onDismiss) {
        Text("Fundar partido", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        Spacer(Modifier.height(10.dp))
        Field(name, "Nome do partido") { name = it.take(40) }
        Spacer(Modifier.height(8.dp))
        Field(sigla, "Sigla") { sigla = it.uppercase().take(10) }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (col in Content.PARTY_COLORS) Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(col)).let { if (col == color) it.border(3.dp, Ink.text, CircleShape) else it }.clickable { color = col },
            )
        }
        Spacer(Modifier.height(6.dp))
        SliderRow(Icons.Rounded.Payments, "Economia", econ, -1f..1f, if (econ < -0.3f) "Estado forte" else if (econ > 0.3f) "Mercado livre" else "Centro") { econ = it }
        SliderRow(Icons.Rounded.Groups, "Costumes", social, -1f..1f, if (social < -0.3f) "Progressista" else if (social > 0.3f) "Conservador" else "Centro") { social = it }
        Text("Número na urna: $number", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(12.dp))
        Btn("Fundar", Modifier.fillMaxWidth(), BtnKind.PRIMARY, enabled = name.isNotBlank() && sigla.isNotBlank()) {
            onCreate(Party("meu_${sigla.lowercase()}", sigla.trim(), name.trim(), number, color, econ, social, 0.05f, custom = true))
        }
    }
}
