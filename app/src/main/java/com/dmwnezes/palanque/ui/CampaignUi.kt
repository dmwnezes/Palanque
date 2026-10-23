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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.sim.Camara
import com.dmwnezes.palanque.sim.Elections
import com.dmwnezes.palanque.sim.Fmt

/** Convenção: escolher o vice e os partidos da coligação. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConventionCard(c: GameController) {
    val s = c.sim
    val e = c.game.election ?: return
    var vice by remember { mutableIntStateOf(0) }
    val partners = remember { mutableStateListOf<String>() }
    val options = remember { Elections.coalitionOptions(s) }
    Overlay({}, dismissable = false) {
        Icon(Icons.Rounded.Groups, null, tint = Ink.gold, modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally))
        Text("Convenção do ${s.party.sigla}", style = Type.title.copy(fontSize = 22.sp), color = Ink.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Sua candidatura à reeleição. Escolha o vice e quem sobe no palanque com você.", style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
            Text("Vice", style = Type.label, color = Ink.muted)
            for ((i, v) in e.viceOptions.withIndex()) {
                val sel = i == vice
                val p = s.partyOf(v.party)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(Shapes.tile).background(if (sel) Ink.gold.copy(alpha = 0.16f) else Ink.raised)
                        .let { if (sel) it.border(1.5.dp, Ink.gold, Shapes.tile) else it }.clickable { vice = i }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(v.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(p.sigla, Color(p.color))
                            Spacer(Modifier.width(6.dp))
                            Text("conhecido ${Fmt.inB(c.game.bairros[v.bairro].name)}", style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Stars(v.fame)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Coligação", style = Type.label, color = Ink.muted)
            Text("Mais partidos = mais verba e tempo de TV. Partidos muito diferentes de você tiram credibilidade.", style = Type.small, color = Ink.faint)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((p, ok) in options) {
                    val sel = p.id in partners
                    val far = Camara.ideoDistance(p.econ, p.social, s.party.econ, s.party.social) > 0.5
                    Row(
                        Modifier.clip(Shapes.chip).background(if (sel) Ink.gold else if (ok) Ink.raised else Ink.oceanDeep)
                            .clickable(enabled = ok) { if (sel) partners.remove(p.id) else partners += p.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.color)))
                        Spacer(Modifier.width(6.dp))
                        Text(p.sigla + if (!ok) " · recusa" else if (far) " · distante" else "", style = Type.label,
                            color = if (sel) Ink.onGold else if (ok) Ink.text else Ink.faint)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Btn("Registrar a chapa", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.HowToVote) {
            Elections.confirmConvention(s, vice, partners.toList()); c.refresh()
        }
    }
}

/** Debate na TV: três perguntas, uma resposta por vez. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebateCard(c: GameController) {
    val s = c.sim
    val e = c.game.election ?: return
    if (e.debate == 0 || e.debateStep >= e.debateAreas.size) return
    val a = e.debateAreas[e.debateStep]
    val rivals = e.candidates.filter { !it.player }.let { rs -> if (e.rival2 >= 0) listOf(e.candidates[e.rival2]) else rs }
    val asker = rivals[(e.debateStep + e.debate) % rivals.size]
    Overlay({}, dismissable = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tv, null, tint = Ink.gold, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (e.debate == 3) "Debate do 2º turno" else "${e.debate}º debate na TV", style = Type.heading, color = Ink.text, modifier = Modifier.weight(1f))
            Text("${e.debateStep + 1} de ${e.debateAreas.size}", style = Type.label, color = Ink.muted)
        }
        Spacer(Modifier.height(12.dp))
        Text("${asker.name} (${s.partyOf(asker.party).sigla}) pergunta:", style = Type.label, color = Ink.muted)
        Text("“O que você fez por ${a.the} da cidade?”", style = Type.title.copy(fontSize = 20.sp), color = Ink.text, modifier = Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GameIcons.area(a), null, tint = Ink.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${a.label} hoje: ${c.game.ind[a.ordinal].toInt()}", style = Type.small, color = Ink.scale(c.game.ind[a.ordinal].toFloat()))
        }
        Spacer(Modifier.height(12.dp))
        Elections.DEBATE_OPTIONS.forEachIndexed { i, label ->
            val hint = Elections.debateHint(s, a, i)
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised)
                .clickable { Elections.answerDebate(s, i); c.refresh() }.padding(12.dp)) {
                Text(label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
                Spacer(Modifier.height(4.dp))
                EffectChip(hint.text, hint.good)
            }
        }
        Text("Placar até aqui: ${Fmt.signed(e.debateScore)}", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Linha do tempo da eleição, para a aba Carreira. */
@Composable
fun CampaignTimeline(c: GameController) {
    val s = c.sim
    val g = c.game
    val dd = Elections.debateDays(s)
    val steps = listOf(
        "Convenção" to Elections.conventionDay(s),
        "Campanha nas ruas" to Elections.campaignStart(s),
        "1º debate" to dd[0],
        "2º debate" to dd[1],
        "1º turno" to s.dayOf(s.firstSunday(s.termEndYear, 10)),
        "2º turno (se houver)" to s.dayOf(s.lastSunday(s.termEndYear, 10)),
    )
    Column(Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
        for ((label, day) in steps) {
            val past = g.day >= day
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (past) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (past) Ink.gold else Ink.faint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, style = Type.label, color = if (past) Ink.text else Ink.muted, modifier = Modifier.weight(1f))
                Text(Fmt.date(s.dateOf(day)), style = Type.small, color = Ink.muted)
            }
        }
    }
}
