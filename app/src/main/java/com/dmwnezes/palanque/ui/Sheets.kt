package com.dmwnezes.palanque.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.palanque.sim.Area
import com.dmwnezes.palanque.sim.Content
import com.dmwnezes.palanque.sim.Elections
import com.dmwnezes.palanque.sim.Fmt
import com.dmwnezes.palanque.sim.Role
import com.dmwnezes.palanque.sim.Secretario
import com.dmwnezes.palanque.world.Stat
import kotlin.math.roundToInt

@Composable
internal fun Tabbed(c: GameController, tab: Tab, title: String, sub: String?, tabs: List<String>, content: @Composable ColumnScope.(Int) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sel = (c.subTab[tab] ?: 0).coerceIn(0, tabs.lastIndex)
    Column(Modifier.fillMaxSize()) {
        SheetHeaderTabs(title, sub, tabs, sel) { c.subTab[tab] = it; c.refresh() }
        Box(Modifier.weight(1f)) { SheetBody { content(sel) } }
    }
}

@Composable
internal fun Note(text: String, color: Color = Ink.muted) {
    Text(text, style = Type.small, color = color, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
internal fun LineItem(label: String, value: String, color: Color = Ink.text, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = Ink.text)
            if (sub != null) Text(sub, style = Type.small, color = Ink.muted)
        }
        Text(value, style = Type.number.copy(fontSize = 15.sp), color = color)
    }
}

@Composable
private fun LimitRow(label: String, value: Double, ok: Boolean, rule: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok && !warn) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (!ok) Ink.coral else if (warn) Ink.amber else Ink.good, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
        Text(rule, style = Type.small, color = Ink.muted)
        Spacer(Modifier.width(10.dp))
        Text(Fmt.pct(value), style = Type.number.copy(fontSize = 15.sp), color = if (!ok) Ink.coral else if (warn) Ink.amber else Ink.text)
    }
}

// ───────────────────────── Governo ─────────────────────────

@Composable
fun GovernoSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sim = c.sim
    val g = c.game
    Tabbed(c, Tab.GOVERNO, "Governo", sim.governmentName, listOf("Orçamento", "Impostos", "Dívida")) { sel ->
        val b = sim.budget()
        when (sel) {
            0 -> {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("Receita por ano", Fmt.money(b.revenue), Ink.text, Modifier.weight(1f))
                    BigStat("Despesa por ano", Fmt.money(b.expense), Ink.text, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("Saldo por ano", Fmt.money(b.balance), if (b.balance >= 0) Ink.good else Ink.coral, Modifier.weight(1f))
                    BigStat("Em caixa", Fmt.money(g.cash), if (g.cash >= 0) Ink.text else Ink.coral, Modifier.weight(1f))
                }
                SectionTitle("Limites da lei")
                val l = sim.limits()
                LimitRow("Saúde", l.health, l.healthOk, "mín. ${(l.healthFloor * 100).roundToInt()}%")
                LimitRow("Educação", l.education, l.educationOk, "mín. ${(l.eduFloor * 100).roundToInt()}%")
                LimitRow("Pessoal (LRF)", l.personnel, l.personnelOk, "máx. ${(l.personnelCap * 100).roundToInt()}%", l.personnelAlert)
                Note("Conferidos no fim de cada ano pelo Tribunal de Contas. Duas rejeições deixam você inelegível." +
                    if (g.rejected > 0) " Rejeições até agora: ${g.rejected}." else "", if (g.rejected > 0) Ink.coral else Ink.muted)
                SectionTitle("Gasto por área")
                Note("100% é o nível de referência do governo. Mais gasto melhora a área devagar, ao longo de meses.")
                for (a in Area.entries) {
                    val lvl = g.spend[a.ordinal]
                    SliderRow(
                        GameIcons.area(a), a.label, lvl.toFloat(), 0.3f..2f, Fmt.money(b.area(a)),
                        hint = "${(lvl * 100).roundToInt()}%", hintGood = lvl >= 1.0,
                    ) { sim.setSpend(a, (it * 20).roundToInt() / 20.0); c.refresh() }
                }
                SectionTitle("Outras despesas")
                LineItem(if (sim.level == com.dmwnezes.palanque.sim.Level.PAIS) "Administração e Previdência" else "Administração", Fmt.money(b.admin))
                LineItem(sim.transferName, Fmt.money(b.camara))
                LineItem("Manutenção das obras", Fmt.money(b.upkeep))
                LineItem("Obras em andamento", Fmt.money(b.works))
                LineItem("Juros e amortização", Fmt.money(b.interest + b.amort))
                if (b.laws > 0) LineItem("Leis em vigor", Fmt.money(b.laws))
                if (b.cargos > 0) LineItem("Cargos comissionados (${g.cargos})", Fmt.money(b.cargos))
            }
            1 -> {
                Spacer(Modifier.height(8.dp))
                val (taName, taDesc) = sim.taxANames
                val (tbName, tbDesc) = sim.taxBNames
                val rb = sim.taxBRange
                SliderRow(when (sim.level) { com.dmwnezes.palanque.sim.Level.CIDADE -> Icons.Rounded.Home; com.dmwnezes.palanque.sim.Level.ESTADO -> Icons.Rounded.DirectionsCar; else -> Icons.Rounded.Work }, taName, g.iptu.toFloat(), 0.6f..1.6f, Fmt.money(b.iptu),
                    hint = when { g.iptu < 0.85 -> "baixo"; g.iptu > 1.15 -> "alto"; else -> "normal" }, hintGood = g.iptu <= 1.15) {
                    sim.setIptu((it * 20).roundToInt() / 20.0); c.refresh()
                }
                SliderRow(Icons.Rounded.Storefront, tbName, g.iss.toFloat(), rb.start.toFloat()..rb.endInclusive.toFloat(), Fmt.money(b.iss),
                    hint = "${"%.1f".format(g.iss).replace('.', ',')}%", hintGood = g.iss <= (rb.start + rb.endInclusive) / 2) {
                    sim.setIss((it * 4).roundToInt() / 4.0); c.refresh()
                }
                Note("Imposto alto tira aprovação. Alíquota alta também aumenta a sonegação e espanta empresas.")
                SectionTitle("De onde vem o dinheiro")
                for ((name, v, sub) in sim.revenueLines(b)) LineItem(name, Fmt.money(v), sub = sub)
            }
            else -> {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("Dívida", Fmt.money(g.debt), Ink.text, Modifier.weight(1f))
                    BigStat("Juros por ano", Fmt.money(b.interest), Ink.amber, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                LineItem("Limite total (${(sim.debtLimitShare * 100).roundToInt()}% da receita)", Fmt.money(sim.debtLimit))
                LineItem("Pode pegar neste ano", Fmt.money(sim.borrowLimit), Ink.gold)
                Note("Juros de ${"%.1f".format(com.dmwnezes.palanque.sim.Sim.INTEREST_MONTH * 100).replace('.', ',')}% ao mês. A dívida é paga em ${com.dmwnezes.palanque.sim.Sim.DEBT_YEARS.toInt()} anos.")
                Spacer(Modifier.height(10.dp))
                val opts = listOf(0.02, 0.05, 0.10).map { b.revenue * it }.filter { it <= sim.borrowLimit + 1 }
                if (opts.isEmpty()) Note("Sem crédito disponível agora.", Ink.coral)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in opts) Btn(Fmt.money(v), Modifier.weight(1f), icon = Icons.Rounded.Add) { c.act { sim.borrow(v) } }
                }
                Spacer(Modifier.height(8.dp))
                Btn("Amortizar ${Fmt.money(minOf(g.debt, b.revenue * 0.05))}", Modifier.fillMaxWidth(), BtnKind.GHOST, Icons.Rounded.Remove,
                    enabled = g.debt > 0 && g.cash > 0) { c.act { sim.repay(b.revenue * 0.05) } }
            }
        }
    }
}

// ───────────────────────── Obras ─────────────────────────

@Composable
fun ObrasSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sim = c.sim
    val g = c.game
    Tabbed(c, Tab.OBRAS, "Obras", "${sim.activeObras} de ${sim.maxObras} em andamento", listOf("Catálogo", "Andamento", "Prontas")) { sel ->
        when (sel) {
            0 -> {
                val target = c.buildTarget
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.raised).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Place, null, tint = Ink.gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (target >= 0) "Construir ${Fmt.inB(g.bairros[target].name)}" else "Escolha a obra e depois toque no ${sim.unitName}",
                        style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
                    if (target >= 0) Text("Trocar", style = Type.label, color = Ink.gold, modifier = Modifier.clip(Shapes.chip).clickable { c.buildTarget = -1; c.refresh() }.padding(6.dp))
                }
                if (sim.obraUnits > 1) Note("Com esta população, cada obra é um lote de ${sim.obraUnits} unidades.")
                for (a in Area.entries) {
                    val list = sim.obras.filter { it.area == a }
                    SectionTitle(a.label)
                    for (t in list) {
                        val cost = sim.obraCost(t)
                        val canStart = sim.activeObras < sim.maxObras && g.cash >= cost * 0.15
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                                .clickable {
                                    if (target >= 0) { c.act { sim.build(t.id, target) }; c.buildTarget = -1; c.select(target); c.openTab(null) }
                                    else { c.openTab(null); c.placing = t.id; c.refresh() }
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Ink.oceanDeep), contentAlignment = Alignment.Center) {
                                Icon(GameIcons.of(t.icon), null, tint = if (canStart) Ink.gold else Ink.faint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text)
                                Text("${t.desc} · atende ${Fmt.people(sim.obraServes(t))}", style = Type.small, color = Ink.muted, maxLines = 2)
                                // efeitos extras e o lado ruim
                                val side = t.extra.map { (a, v) -> "${a.short} ${Fmt.signed(v)}" } +
                                    listOfNotNull(if (t.nimby > 0) "vizinhos reclamam" else null, if (t.upkeep > 0.06) "manutenção cara" else null)
                                if (side.isNotEmpty()) Text(side.joinToString(" · "), style = Type.small, color = Ink.gold, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.money(cost), style = Type.number.copy(fontSize = 14.sp), color = if (canStart) Ink.text else Ink.coral)
                                Text("${t.months} meses", style = Type.small, color = Ink.muted)
                            }
                        }
                    }
                }
                Note("Paga 15% na largada e o resto mês a mês. Pronta, a obra custa em geral 6% do valor por ano em manutenção.")
            }
            1 -> {
                val list = g.obras.filter { !it.done }
                if (list.isEmpty()) Note("Nenhuma obra em andamento.")
                for (o in list) {
                    val t = Content.obra(o.type)
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).clickable { c.select(o.bairro); c.openTab(null) }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(GameIcons.of(t.icon), null, tint = Ink.gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                            Text("${(o.progress * 100).toInt()}%", style = Type.number.copy(fontSize = 14.sp), color = Ink.gold)
                        }
                        Text(g.bairros[o.bairro].name + if (o.share < 1) " · convênio com o estado" else "", style = Type.small, color = Ink.muted)
                        Meter((o.progress * 100).toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp), Ink.gold)
                    }
                }
                if (g.cash <= 0 && list.isNotEmpty()) Note("Obras paradas: o caixa está vazio.", Ink.coral)
            }
            else -> {
                val list = g.obras.filter { it.done }
                if (list.isEmpty()) Note("Nenhuma obra entregue ainda.")
                for (o in list.reversed()) {
                    val t = Content.obra(o.type)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).clickable { c.select(o.bairro); c.openTab(null) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(GameIcons.of(t.icon), null, tint = Ink.gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = Type.body, color = Ink.text)
                            Text(g.bairros[o.bairro].name, style = Type.small, color = Ink.muted)
                        }
                        Text(t.area.short, style = Type.small, color = Ink.muted)
                    }
                }
            }
        }
    }
}

// ───────────────────────── Equipe ─────────────────────────

@Composable
private fun SecretarioLine(c: GameController, s: Secretario) {
    val p = c.sim.partyOf(s.party)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(p.sigla, Color(p.color))
                Spacer(Modifier.width(8.dp))
                Text("Competência", style = Type.small, color = Ink.muted)
                Spacer(Modifier.width(4.dp))
                Stars(s.competence)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Honestidade", style = Type.small, color = Ink.muted)
                Spacer(Modifier.width(4.dp))
                Stars(s.honesty, Ink.teal)
                Spacer(Modifier.width(8.dp))
                Text("Lealdade", style = Type.small, color = Ink.muted)
                Spacer(Modifier.width(4.dp))
                Stars(s.loyalty, Ink.muted)
            }
        }
    }
}

@Composable
fun EquipeSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sim = c.sim
    val g = c.game
    var open by remember { mutableStateOf<Area?>(g.secretarios.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { Area.entries[it] }) }
    val vagas = g.secretarios.count { it == null }
    val dept = c.sim.deptName
    Tabbed(c, Tab.EQUIPE, "Equipe", if (vagas > 0) "$vagas ${dept}s vag${if (dept == "ministério") "o" else "a"}s" else "Equipe completa", listOf(dept.replaceFirstChar { it.uppercase() } + "s")) { _ ->
        Note("Competência deixa o dinheiro render mais. Honestidade baixa pode virar escândalo. Sem ${if (dept == "ministério") "ministro" else "secretário"} a área perde 10% de eficiência.")
        for (a in Area.entries) {
            val s = g.secretarios[a.ordinal]
            val expanded = open == a
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { open = if (expanded) null else a }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(GameIcons.area(a), null, tint = if (s == null) Ink.coral else Ink.gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(a.label, style = Type.label, color = Ink.muted, modifier = Modifier.weight(1f))
                    Text("Eficiência ${Fmt.pct(sim.efficiency(a))}", style = Type.small, color = if (sim.efficiency(a) >= 1) Ink.good else Ink.amber)
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Ink.muted)
                }
                Spacer(Modifier.height(6.dp))
                if (s != null) {
                    SecretarioLine(c, s)
                    if (expanded) Btn("Demitir", Modifier.padding(top = 8.dp), BtnKind.GHOST) { sim.fire(a); c.refresh() }
                } else Text("Vaga", style = Type.body, color = Ink.coral)
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (s == null) "Candidatos" else "Para substituir", style = Type.small, color = Ink.muted)
                    for (cand in g.pool.filter { it.area == a }) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(Shapes.small).background(Ink.oceanDeep).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SecretarioLine(c, cand) }
                            Btn("Nomear", kind = BtnKind.PRIMARY) { c.act { sim.hire(cand.id) }; open = null }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Cidade ─────────────────────────

@Composable
fun CidadeSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sim = c.sim
    val g = c.game
    val city = sim.place
    Tabbed(c, Tab.CIDADE, city.name, "${Fmt.people(g.pop)} habitantes" + if (sim.level == com.dmwnezes.palanque.sim.Level.CIDADE) " · ${sim.state.name}" else "",
        listOf("Áreas", sim.unitsName.replaceFirstChar { it.uppercase() }, "Notícias", "Dados reais")) { sel ->
        when (sel) {
            0 -> {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("Aprovação", "${g.approval.toInt()}%", Ink.scale(g.approval.toFloat()), Modifier.weight(1f))
                    BigStat("Satisfação", "${sim.satisfaction().toInt()}", Ink.scale(sim.satisfaction().toFloat()), Modifier.weight(1f))
                }
                SectionTitle("Como está cada área")
                for (a in Area.entries) {
                    val v = g.ind[a.ordinal]
                    MeterRow(GameIcons.area(a), a.label, v.toFloat(), v.toInt().toString(), (sim.target(a) - v).toFloat())
                }
                Note("A seta mostra para onde a área caminha com o gasto, a equipe e as obras de hoje.")
                if (g.history.size >= 2) {
                    Spacer(Modifier.height(8.dp))
                    LineChart("Aprovação", g.history.takeLast(48).map { it.approval.toFloat() }, "${g.approval.toInt()}%")
                }
            }
            1 -> {
                Spacer(Modifier.height(6.dp))
                for (i in g.bairros.indices.sortedByDescending { g.bairros[it].approval }) {
                    val b = g.bairros[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).clickable { c.select(i); c.openTab(null) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = Type.body, color = Ink.text)
                            Text("${Fmt.people(g.pop * b.popShare)} · quer ${b.need.short.lowercase()}", style = Type.small, color = Ink.muted)
                            Meter(b.approval.toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${b.approval.toInt()}%", style = Type.number, color = Ink.scale(b.approval.toFloat()))
                    }
                }
            }
            2 -> {
                Spacer(Modifier.height(6.dp))
                for (f in g.feed.take(60)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(GameIcons.of(f.icon), null, tint = Ink.tone(f.tone), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.text, style = Type.body, color = Ink.text)
                            Text(Fmt.date(sim.dateOf(f.day)), style = Type.small, color = Ink.faint)
                        }
                    }
                }
            }
            else -> {
                Spacer(Modifier.height(6.dp))
                Note("Ponto de partida do jogo: os números reais da cidade.")
                fun v(s: Stat) = city.stat(s)
                fun ok(s: Stat) = !v(s).isNaN()
                if (ok(Stat.POP)) LineItem("População estimada", Fmt.people(v(Stat.POP).toDouble()))
                if (ok(Stat.AREA)) LineItem("Área", "${Fmt.people(v(Stat.AREA).toDouble())} km²")
                if (ok(Stat.PIB)) LineItem("PIB", Fmt.money(v(Stat.PIB).toDouble()))
                if (ok(Stat.PIB_PC)) LineItem("PIB por habitante", Fmt.money(v(Stat.PIB_PC).toDouble()))
                if (ok(Stat.RENDA)) LineItem("Renda média por pessoa", Fmt.money(v(Stat.RENDA).toDouble()) + "/mês")
                if (ok(Stat.IDH)) LineItem("IDH", "%.3f".format(v(Stat.IDH)).replace('.', ','))
                if (ok(Stat.ALFAB)) LineItem("Alfabetização", "${"%.1f".format(v(Stat.ALFAB)).replace('.', ',')}%")
                if (ok(Stat.AGUA)) LineItem("Água da rede geral", "${v(Stat.AGUA).roundToInt()}%")
                if (ok(Stat.ESGOTO)) LineItem("Esgoto adequado", "${v(Stat.ESGOTO).roundToInt()}%")
                if (ok(Stat.HOMIC)) LineItem("Homicídios por 100 mil", "${"%.1f".format(v(Stat.HOMIC)).replace('.', ',')}")
                if (ok(Stat.ELEITORES)) LineItem("Eleitores", Fmt.people(v(Stat.ELEITORES).toDouble()))
                Note("Fontes: IBGE (Censo 2022, estimativas, PIB dos Municípios), PNUD/Ipea (IDH), Ipea/FBSP (Atlas da Violência), TSE.", Ink.faint)
            }
        }
    }
}

// ───────────────────────── Carreira ─────────────────────────

@Composable
fun CarreiraSheet(c: GameController) {
    // lê o relógio da tela: sem isso os números calculados aqui só mudam ao reabrir a aba
    @Suppress("UNUSED_EXPRESSION") c.tick
    val sim = c.sim
    val g = c.game
    val e = g.election
    Tabbed(c, Tab.CARREIRA, "Carreira", "${g.player.name} · ${sim.party.sigla}", listOf("Mandato", "Eleição", "Mídia", "Bastidores")) { sel ->
        if (sel == 2) { MidiaPane(c); return@Tabbed }
        if (sel == 3) { BastidoresPane(c); return@Tabbed }
        if (sel == 0) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat("Cargo", if (g.regime) "Ditador" else if (g.inOffice) g.role.label else "Sem mandato", Ink.gold, Modifier.weight(1f))
                BigStat("Mandato", if (g.inOffice) "${g.termNumber}º" else "—", Ink.text, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            val ey = Elections.year(sim)
            val eday = Elections.firstRoundDay(sim, ey)
            LineItem("Próxima eleição", "${Fmt.date(sim.dateOf(eday))} · ${if (Elections.isMunicipal(ey)) "municipal" else "geral"}",
                sub = (eday - g.day).takeIf { it > 0 }?.let { "em $it dias" })
            if (g.regime) LineItem("Fim do mandato", "sem fim", Ink.coral, sub = "regime desde ${sim.dateOf(g.regimeSince).year}")
            else if (g.inOffice) LineItem("Fim do mandato", "31 dez ${sim.termEndYear}")
            val opts = Elections.options(sim, ey)
            LineItem("Pode disputar", if (opts.isEmpty()) (if (g.career.ineligibleUntil >= ey) "nada: inelegível" else "—") else opts.joinToString(" · ") { it.label.lowercase() },
                if (opts.isEmpty()) Ink.coral else Ink.good)
            if (g.career.history.isNotEmpty()) {
                SectionTitle("Cargos já exercidos")
                for (h in g.career.history.reversed()) LineItem(h.role.label, "${sim.dateOf(h.from).year}–${sim.dateOf(h.to).year}", sub = "${h.place} · saiu com ${h.approval.toInt()}%")
            }
            SectionTitle("A escada")
            for (r in Role.entries) {
                val now = r == g.role
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (now) Ink.gold.copy(alpha = 0.14f) else Ink.raised).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (now) Icons.Rounded.Star else if (r.playable) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null,
                        tint = if (now) Ink.gold else Ink.faint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = Type.body, color = if (now) Ink.text else Ink.muted)
                        Text(r.desc, style = Type.small, color = Ink.faint)
                    }
                    if (!r.playable) Text("em breve", style = Type.small, color = Ink.faint)
                    else if (r == Role.PRESIDENTE && !Elections.canRunForPresident(g)) Text("antes: governador, senador ou dep. federal", style = Type.small, color = Ink.faint)
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            CampaignTimeline(c)
            if (g.campaignPromises.isNotEmpty()) {
                SectionTitle("Promessas de campanha")
                for (p in g.campaignPromises) {
                    val now = g.ind[p.area.ordinal]
                    LineItem("${p.area.label} +5", if (p.done) (if (now >= p.base + 5) "cumprida" else "quebrada") else "${now.toInt()} / ${(p.base + 5).toInt()}",
                        if (now >= p.base + 5) Ink.good else if (p.done) Ink.coral else Ink.amber, sub = "prazo: ${Fmt.date(sim.dateOf(p.due))}")
                }
            }
            if (e == null) {
                Note("Em 20 de julho de ${Elections.year(sim)} você escolhe o que disputar. Até lá, " +
                    if (sim.governs) "governe bem: a aprovação de cada ${sim.unitName} vira voto." else "cuide do seu apoio: emendas, discursos e votações.")
                return@Tabbed
            }
            e.vice?.let { v ->
                SectionTitle("Chapa")
                LineItem(if (e.office == Role.SENADOR) "Suplente" else "Vice", v.name, sub = sim.partyOf(v.party).sigla)
                LineItem("Coligação", if (e.partners.isEmpty()) "só o ${sim.party.sigla}" else e.partners.joinToString(" · ") { sim.partyOf(it).sigla })
                LineItem("Tempo de TV", Fmt.pct(e.tv), sub = "a propaganda rende mais com mais tempo")
                if (Elections.incoherence(sim, e) > 0) Note("Partidos distantes na coligação tiram ${Fmt.signed(-Elections.incoherence(sim, e))} pontos.", Ink.amber)
            }
            val pl = e.candidates.indexOfFirst { it.player }
            LineItem("Disputa", e.office.label, Ink.gold, sub = if (e.proportional) "proporcional" else "majoritária")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigStat("Verba de campanha", Fmt.money(e.fund), Ink.gold, Modifier.weight(1f))
                BigStat(if (e.finished) "Resultado" else "Faltam", if (e.finished) (if (e.won) "Eleito" else "Derrota") else "${(if (e.round == 1) e.runoffDay else e.day) - g.day} dias",
                    if (e.finished && !e.won) Ink.coral else Ink.text, Modifier.weight(1f))
            }
            SectionTitle("Pesquisa")
            val last = e.polls.lastOrNull() ?: e.totals.takeIf { it.isNotEmpty() }
            if (e.proportional) {
                if (e.totals.size == 2) LineItem("Resultado", "${Fmt.people(e.totals[0])} votos", sub = "quociente: ${Fmt.people(e.totals[1])}")
                else if (e.polls.isEmpty()) Note("A primeira pesquisa sai uma semana depois do início da campanha.")
                else {
                    val q = e.polls.last()[0]
                    LineItem("Votos estimados", "${(q * 100).toInt()}% do quociente", if (q >= 0.7) Ink.good else if (q >= 0.4) Ink.amber else Ink.coral,
                        sub = "60% elege; 40% elege se o partido for grande")
                    if (e.polls.size >= 2) LineChart("Votos estimados (% do quociente)", e.polls.map { (it[0] * 100).toFloat() }, "${(q * 100).toInt()}%")
                }
            }
            else if (last == null) Note("A primeira pesquisa sai em uma semana.")
            else for (i in e.candidates.indices.sortedByDescending { last[it] }) {
                val cand = e.candidates[i]
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cand.name + if (cand.player) " (você)" else "", style = Type.body, color = if (cand.player) Ink.gold else Ink.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(sim.partyOf(cand.party).sigla, style = Type.small, color = Ink.muted)
                        Spacer(Modifier.width(10.dp))
                        Text(Fmt.pct(last[i]), style = Type.number, color = Ink.text)
                    }
                    Meter((last[i] * 100).toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp), if (cand.player) Ink.gold else Ink.muted)
                }
            }
            if (!e.proportional && e.polls.size >= 2) {
                Spacer(Modifier.height(8.dp))
                LineChart("Sua intenção de voto", e.polls.map { (it[pl] * 100).toFloat() }, Fmt.pct(e.polls.last()[pl]))
            }
            if (!e.finished) {
                SectionTitle("Campanha")
                Btn("Propaganda na TV e nas redes · ${Fmt.money(Elections.adCost(sim))}", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Tv,
                    enabled = e.fund >= Elections.adCost(sim) && e.boost < 6) { c.act("Propaganda no ar") { Elections.advertise(sim) } }
                Note("Comícios: toque num ${sim.unitName} no mapa e escolha Comício (${Fmt.money(Elections.rallyCost(sim))}, até 2 em cada).")
            }
        }
    }
}
