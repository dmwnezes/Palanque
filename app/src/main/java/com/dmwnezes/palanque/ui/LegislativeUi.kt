package com.dmwnezes.palanque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.sim.Area
import com.dmwnezes.palanque.sim.Elections
import com.dmwnezes.palanque.sim.Fmt
import com.dmwnezes.palanque.sim.Legislativo
import com.dmwnezes.palanque.sim.Role

/** Emendas parlamentares: escolher região, área e quanto mandar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmendasSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val s = c.sim
    val g = c.game
    var area by remember { mutableStateOf(g.bairros.getOrNull(c.emendaTarget)?.need ?: Area.SAUDE) }
    var share by remember { mutableIntStateOf(1) }
    val budget = Legislativo.emendaBudget(s)
    Tabbed(c, Tab.EMENDAS, "Emendas", "Restam ${Fmt.money(g.emendasLeft)} de ${Fmt.money(budget)} neste ano", listOf("Mandar", "Histórico")) { sel ->
        if (sel == 0) {
            Spacer(Modifier.height(6.dp))
            Note("Emenda vira apoio no lugar que recebe e melhora um pouco a área. O governo paga ${Fmt.pct(Legislativo.payFactor(s))} do valor com você ${when (g.stance) { 1 -> "na base"; -1 -> "na oposição"; else -> "independente" }}.")
            SectionTitle("Para onde")
            val target = c.emendaTarget.takeIf { it in g.bairros.indices }
            if (target == null) Note("Toque num ${s.unitName} no mapa e escolha \"Emenda aqui\", ou escolha abaixo.")
            val units = g.bairros.indices.sortedByDescending { g.bairros[it].popShare }.take(if (g.bairros.size > 30) 30 else g.bairros.size)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in (listOfNotNull(target) + units).distinct()) {
                    val sel2 = i == target
                    Text(g.bairros[i].name, style = Type.label, color = if (sel2) Ink.onGold else Ink.text, maxLines = 1,
                        modifier = Modifier.clip(Shapes.chip).background(if (sel2) Ink.gold else Ink.raised).clickable { c.emendaTarget = i; area = g.bairros[i].need; c.refresh() }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            SectionTitle("Para quê")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in Area.entries) {
                    val on = a == area
                    Row(Modifier.clip(Shapes.chip).background(if (on) Ink.gold else Ink.raised).clickable { area = a }.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(GameIcons.area(a), null, tint = if (on) Ink.onGold else Ink.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(a.short, style = Type.label, color = if (on) Ink.onGold else Ink.text)
                    }
                }
            }
            target?.let { Note("Moradores ${Fmt.ofB(g.bairros[it].name)} querem mais ${g.bairros[it].need.label.lowercase()}: emenda nessa área rende mais apoio.") }
            SectionTitle("Quanto")
            val fractions = listOf(0.1, 0.25, 0.5, 1.0)
            Segmented(fractions.indices.toList(), share, { listOf("10%", "25%", "metade", "tudo")[it] }) { share = it }
            val amount = g.emendasLeft * fractions[share]
            Spacer(Modifier.height(12.dp))
            Btn("Mandar ${Fmt.money(amount)}", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Send, enabled = target != null && amount > 0) {
                target?.let { t -> c.act { Legislativo.sendEmenda(s, t, area, amount) } }
            }
            Note("O valor das emendas renova em 1º de janeiro.")
        } else {
            Spacer(Modifier.height(6.dp))
            if (g.emendas.isEmpty()) Note("Nenhuma emenda enviada ainda.")
            for (e in g.emendas.reversed()) LineItem(g.bairros.getOrNull(e.unit)?.name ?: "—", Fmt.money(e.amount), sub = "${e.area.label} · ${Fmt.date(s.dateOf(e.day))}")
        }
    }
}

/** Atuação no mandato: posição diante do governo, tribuna, CPI, relação com partido e governo. */
@Composable
fun MandatoSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val s = c.sim
    val g = c.game
    Tabbed(c, Tab.MANDATO, "Mandato", "${g.role.label} · ${s.party.sigla}", listOf("Atuação")) { _ ->
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigStat("Capital político", "${g.career.capital.toInt()}", Ink.gold, Modifier.weight(1f))
            BigStat("Seu apoio", "${g.approval.toInt()}%", Ink.scale(g.approval.toFloat() * 1.4f), Modifier.weight(1f))
        }
        SectionTitle("Diante do governo")
        LineItem("${s.execTitle.replaceFirstChar { it.uppercase() }}: ${g.execName}", s.partyOf(g.execParty).sigla, sub = "aprovação ${g.execApproval.toInt()}%")
        LineItem("Relação do governo com você", Fmt.signed(g.execRel), if (g.execRel >= 0) Ink.good else Ink.coral)
        Spacer(Modifier.height(6.dp))
        Segmented(listOf(1, 0, -1), g.stance, { when (it) { 1 -> "Base"; -1 -> "Oposição"; else -> "Independente" } }) { Legislativo.setStance(s, it); c.refresh() }
        Note(when (g.stance) {
            1 -> "Na base: emendas pagas por inteiro e o seu apoio acompanha a aprovação do governo."
            -1 -> "Na oposição: o governo segura parte das emendas, mas você cresce quando ele vai mal."
            else -> "Independente: um pouco de cada lado."
        })
        SectionTitle("Ações")
        ActionRow(Icons.Rounded.Mic, "Discurso na tribuna", if (Legislativo.canSpeak(s)) "Capital +2 e um pouco de apoio · uma vez por mês" else "O próximo só em ${30 - (g.day - g.lastSpeech)} dias", Legislativo.canSpeak(s)) {
            c.act { Legislativo.speech(s) }
        }
        ActionRow(Icons.Rounded.Gavel, "Pedir uma CPI contra o governo", if (g.stance == 1) "Quem é da base não pede CPI" else if (Legislativo.canCpi(s)) "Capital +4 e apoio; o governo fica contra você · uma por ano" else "Já houve CPI neste ano",
            Legislativo.canCpi(s), danger = true) { c.act { Legislativo.cpi(s) } }
        SectionTitle("Partido")
        LineItem("Relação com o ${s.party.sigla}", Fmt.signed(g.partyRel), if (g.partyRel >= 0) Ink.good else Ink.coral, sub = "seguir a orientação nas votações aproxima; contrariar afasta")
        Note("Partido satisfeito põe mais dinheiro na sua campanha.")
    }
}

/** Ano de eleição: qual cargo disputar. */
@Composable
fun CandidacyCard(c: GameController) {
    val s = c.sim
    val g = c.game
    val y = Elections.year(s)
    val opts = Elections.options(s, y)
    Overlay({}, dismissable = false) {
        Icon(Icons.Rounded.HowToVote, null, tint = Ink.gold, modifier = Modifier.size(42.dp).align(Alignment.CenterHorizontally))
        Text("Eleições de $y", style = Type.title.copy(fontSize = 22.sp), color = Ink.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(if (Elections.isMunicipal(y)) "Eleição municipal em ${s.br.city(g.career.homeCity)?.name}" else "Eleição geral ${Fmt.ofState(s.state.code, s.state.name)}",
            style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            for (r in opts) {
                val again = g.inOffice && g.role == r
                val leaves = g.inOffice && r.level != g.level
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised).clickable { Elections.declare(s, r); c.refresh() }.padding(14.dp)) {
                    Text(if (again) "Reeleição para ${r.label.lowercase()}" else r.label, style = Type.heading.copy(fontSize = 17.sp), color = Ink.text)
                    Text(r.desc + when {
                        r.executive -> " · majoritária, com vice e coligação"
                        else -> " · proporcional, pelo quociente"
                    }, style = Type.small, color = Ink.muted)
                    if (leaves) Text("Você deixa o cargo de ${g.role.label.lowercase()} agora para disputar.", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 4.dp))
                }
            }
            val continues = g.inOffice && s.termEndYear > y
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).border(1.dp, Ink.line, Shapes.tile).clickable { Elections.declare(s, null); c.refresh() }.padding(14.dp)) {
                Text("Não disputar", style = Type.heading.copy(fontSize = 17.sp), color = Ink.text)
                Text(if (continues) "Seu mandato segue até ${s.termEndYear}." else if (g.inOffice) "Seu mandato termina em 31 de dezembro." else "Você espera a próxima eleição.",
                    style = Type.small, color = Ink.muted)
            }
        }
    }
}

/** Sem mandato: pular até a próxima eleição ou encerrar a carreira. */
@Composable
fun GapCard(c: GameController) {
    val s = c.sim
    val g = c.game
    Overlay({}, dismissable = false) {
        Icon(Icons.Rounded.HourglassBottom, null, tint = Ink.muted, modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally))
        Text("Fora do cargo", style = Type.title.copy(fontSize = 22.sp), color = Ink.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Seu mandato acabou. A carreira continua na próxima eleição.", style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (g.career.history.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            for (h in g.career.history.takeLast(4)) LineItem(h.role.label, "${s.dateOf(h.from).year}–${s.dateOf(h.to).year}", sub = "${h.place} · saiu com ${h.approval.toInt()}%")
        }
        Spacer(Modifier.height(12.dp))
        for (y in Elections.nextElections(s)) {
            val kind = if (Elections.isMunicipal(y)) "Municipal: vereador ou prefeito" else "Geral: deputado, senador, governador ou presidente"
            ActionRow(Icons.Rounded.HowToVote, "Esperar a eleição de $y", kind, true, active = y == Elections.nextElections(s).first()) { Elections.waitUntil(s, y); c.refresh() }
        }
        Btn("Encerrar a carreira", Modifier.fillMaxWidth().padding(top = 6.dp), BtnKind.GHOST) { Elections.retire(s); c.refresh() }
        if (g.career.ineligibleUntil > s.date.year) Text("Inelegível até ${g.career.ineligibleUntil}.", style = Type.small, color = Ink.coral, modifier = Modifier.padding(top = 6.dp))
    }
}
