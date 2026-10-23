package com.dmwnezes.palanque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmwnezes.palanque.sim.Camara
import com.dmwnezes.palanque.sim.Esquemas
import com.dmwnezes.palanque.sim.Fmt
import com.dmwnezes.palanque.sim.Manobras
import com.dmwnezes.palanque.sim.Midia

/** Imprensa e redes: medidores e o que fazer com eles. */
@Composable
fun MidiaPane(c: GameController) {
    val s = c.sim
    val g = c.game
    Spacer(Modifier.height(6.dp))
    MeterRow(Icons.Rounded.Newspaper, "Imprensa", g.press.toFloat())
    MeterRow(Icons.Rounded.Forum, "Redes sociais", g.social.toFloat())
    val w = Midia.weight(g)
    Note("A imprensa segue os fatos: serviços, aprovação e escândalos. As redes são rápidas e emocionais. " +
        "Hoje a mídia vale ${Fmt.signed(w)} pontos ${if (s.governs) "de aprovação" else "de apoio"}.", if (w < -1) Ink.coral else Ink.muted)
    SectionTitle("Ações")
    ActionRow(Icons.Rounded.Mic, "Dar uma entrevista",
        if (Midia.canInterview(s)) (if (s.governs) "Rende quando os serviços vão bem" else "Rende com capital político alto") + " · uma por mês"
        else "A próxima só em ${30 - (g.day - g.lastInterview)} dias", Midia.canInterview(s)) { c.act { Midia.interview(s) } }
    if (s.governs) ActionRow(Icons.Rounded.Tv, "Propaganda do governo · ${Fmt.money(Midia.adCost(s))}",
        if (Midia.canAd(s)) "Redes +8, imprensa −2 · a cada dois meses" else "A próxima só em ${60 - (g.day - g.lastAd)} dias", Midia.canAd(s)) { c.act { Midia.advertise(s) } }
    ToggleRow("Marketing digital", g.marketing, Icons.Rounded.Campaign,
        sub = if (s.governs) "Redes sobem até +12 · ${Fmt.money(Midia.marketingCost(s))} por mês" else "Sua equipe cuida das redes: até +6, sem custo") {
        Midia.setMarketing(s, it); c.refresh()
    }
}

/** Esquemas de corrupção e manobras políticas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BastidoresPane(c: GameController) {
    val s = c.sim
    val g = c.game
    Spacer(Modifier.height(6.dp))
    MeterRow(Icons.Rounded.Policy, "Risco de investigação", g.risk.toFloat(), "${g.risk.toInt()}%")
    if (g.caixa2 > 0) LineItem("Caixa 2 guardado", Fmt.money(g.caixa2), Ink.amber, sub = "dá para usar na próxima campanha")
    Note(when {
        g.risk <= 0 -> "Ficha limpa. Cada esquema rende agora e acumula risco; ele cai devagar com o tempo."
        g.investigated > 0 -> "Já há uma investigação aberta. A próxima descoberta vira operação da Polícia Federal."
        else -> "Chance de alguém descobrir neste mês: ${(Esquemas.monthlyChance(g) * 100).toInt()}%. Uma denúncia vem primeiro; a segunda vira operação policial."
    }, if (g.risk >= 40) Ink.coral else Ink.muted)

    SectionTitle("Esquemas")
    val opts = Esquemas.options(s)
    if (opts.isEmpty()) Note("Sem mandato nem campanha, não há o que desviar.")
    for (sc in opts) ActionRow(Icons.Rounded.Payments, sc.name,
        "${sc.desc} · ${sc.gain} · risco +${sc.risk.toInt()}" + if (!Esquemas.canRun(s)) " · espere ${Esquemas.COOLDOWN - (g.day - g.lastScheme)} dias" else "",
        Esquemas.canRun(s), danger = true) { c.act { Esquemas.run(s, sc.id) } }

    SectionTitle("Troca de partido")
    val block = Manobras.switchBlock(s)
    Note(block ?: "Janela aberta. Trocar custa capital político (−5) e um pouco de apoio; a bancada do partido novo passa a gostar mais de você.",
        if (block != null) Ink.muted else Ink.good)
    if (block == null) {
        var pick by remember { mutableStateOf<String?>(null) }
        val parties = g.parties.filter { (!it.custom || it.id == g.player.party) && it.id != g.player.party }.sortedByDescending { it.size }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in parties) {
                val sel = pick == p.id
                Text(p.sigla, style = Type.label, color = if (sel) Ink.onGold else Ink.text,
                    modifier = Modifier.clip(Shapes.chip).background(if (sel) Ink.gold else Ink.raised)
                        .border(1.dp, Color(p.color).copy(alpha = 0.7f), Shapes.chip).clickable { pick = p.id }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Btn(pick?.let { "Filiar-se ao ${s.partyOf(it).sigla}" } ?: "Escolha o partido", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.SwapHoriz,
            enabled = pick != null) { pick?.let { id -> c.act { Manobras.switchParty(s, id) } }; pick = null }
    }

    if (s.legislates) {
        SectionTitle("Impeachment")
        val ib = Manobras.impeachBlock(s)
        Note("Afastar o ${s.execTitle} ${g.execName} exige 2/3 da ${Camara.houseName(g)}. Dá capital político se passar; se falhar, o governo não esquece.")
        ActionRow(Icons.Rounded.Gavel, "Abrir processo de impeachment", ib ?: "Capital −10 · votos prováveis: ${Manobras.impeachVotes(s)} de ${g.camara.size + 1}",
            ib == null, danger = true) { c.act { Manobras.impeach(s) } }
    }
}
