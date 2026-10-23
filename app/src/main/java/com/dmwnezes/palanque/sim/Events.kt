package com.dmwnezes.palanque.sim

import kotlin.random.Random

/** Efeito mostrado no botão (verde = bom, vermelho = ruim). */
class Chip(val text: String, val good: Boolean)

class EventOption(
    val label: String,
    val chips: (Sim, PendingEvent) -> List<Chip> = { _, _ -> emptyList() },
    val enabled: (Sim, PendingEvent) -> Boolean = { _, _ -> true },
    val apply: (Sim, PendingEvent) -> Unit,
)

class EventDef(
    val key: String,
    val icon: String,
    val crisis: Crisis?,
    val title: (Sim, PendingEvent) -> String,
    val text: (Sim, PendingEvent) -> String,
    val options: List<EventOption>,
    val weight: (Sim) -> Double = { 1.0 },
    val make: (Sim, Random) -> PendingEvent? = { s, _ -> PendingEvent("", s.game.day) },
    /** só para cidades ou só para estados (null = os dois) */
    val level: Level? = null,
    /** 0 = decisões de quem governa, 1 = de parlamentar */
    val who: Int = 0,
)

/** Acontecimentos que pedem uma decisão de quem governa — ou do parlamentar. */
object Events {
    private fun money(v: Double, good: Boolean = false) = Chip((if (good) "+" else "−") + Fmt.money(v).removePrefix("−"), good)
    private fun aprov(v: Double, where: String = "") = Chip("${Fmt.signed(v)} aprovação$where", v >= 0)
    private fun area(a: Area, v: Double) = Chip("${a.short} ${Fmt.signed(v)}", v >= 0)

    private fun Sim.bname(e: PendingEvent) = game.bairros.getOrNull(e.bairro)?.name ?: ""
    private fun Sim.bump(a: Area, v: Double) { game.ind[a.ordinal] = (game.ind[a.ordinal] + v).coerceIn(1.0, 99.0) }
    private fun Sim.legacy(a: Area, v: Double) { game.legacy[a.ordinal] += v }
    private fun Sim.pay(v: Double) { game.cash -= v }

    /** Bairro sorteado com peso pela população. */
    private fun pickBairro(s: Sim, r: Random): Int {
        var x = r.nextDouble()
        s.game.bairros.forEachIndexed { i, b -> x -= b.popShare; if (x <= 0) return i }
        return 0
    }

    private fun scaled(s: Sim, perCapita: Double, min: Double = 150_000.0, max: Double = 80e6) = (s.game.pop * perCapita).coerceIn(min, max)

    private val NORDESTE = 21..29

    val ALL: List<EventDef> = listOf(
        EventDef(
            "temporal", "flood", Crisis.ENCHENTE,
            title = { s, e -> "Temporal alaga ${Fmt.theB(s.bname(e))}" },
            text = { _, _ -> "Ruas cobertas de água, famílias fora de casa. Os moradores esperam uma resposta hoje." },
            weight = { s -> if (s.coverage(Area.SANEAMENTO) > s.game.pop * 0.5) 0.4 else 1.0 },
            make = { s, r -> PendingEvent("temporal", s.game.day, bairro = pickBairro(s, r), amount = scaled(s, 9.0)) },
            options = listOf(
                EventOption("Mutirão de limpeza e reparo", { _, e -> listOf(money(e.amount), aprov(6.0, " no bairro")) }) { s, e -> s.pay(e.amount); s.shift(e.bairro, 6.0) },
                EventOption("Pedir ajuda à Defesa Civil do estado", { _, e -> listOf(money(e.amount * 0.4), aprov(2.0, " no bairro"), aprov(-1.0)) }) { s, e ->
                    s.pay(e.amount * 0.4); s.shift(e.bairro, 3.0); s.shift(-1, -1.0)
                },
                EventOption("Esperar a água baixar", { _, _ -> listOf(aprov(-10.0, " no bairro"), area(Area.SANEAMENTO, -1.0)) }) { s, e -> s.shift(e.bairro, -10.0); s.legacy(Area.SANEAMENTO, -1.0) },
            ),
        ),
        EventDef(
            "seca", "sun", Crisis.SECA,
            title = { _, _ -> "Seca esvazia os açudes" },
            text = { _, _ -> "A zona rural está sem água e o rodízio já chegou às torneiras da cidade." },
            weight = { s -> if (s.city.uf in NORDESTE) 1.4 else 0.15 },
            make = { s, _ -> PendingEvent("seca", s.game.day, amount = scaled(s, 6.0)) },
            options = listOf(
                EventOption("Contratar carros-pipa", { _, e -> listOf(money(e.amount), aprov(2.0)) }) { s, e -> s.pay(e.amount); s.shift(-1, 2.0) },
                EventOption("Decretar emergência e pedir verba federal", { _, e -> listOf(money(e.amount * 0.3, true), aprov(-1.0)) }) { s, e -> s.game.cash += e.amount * 0.3; s.shift(-1, -1.0) },
                EventOption("Esperar a chuva", { _, _ -> listOf(aprov(-6.0), area(Area.SAUDE, -3.0)) }) { s, _ -> s.shift(-1, -6.0); s.bump(Area.SAUDE, -3.0) },
            ),
        ),
        EventDef(
            "greve_edu", "strike", Crisis.GREVE, weight = { 0.6 },
            title = { _, _ -> "Professores entram em greve" },
            text = { _, _ -> "O sindicato pede reajuste. As escolas da rede municipal estão fechadas." },
            options = listOf(
                EventOption("Dar reajuste de 8%", { _, _ -> listOf(Chip("Educação +8% no custo", false), area(Area.EDUCACAO, 2.0), aprov(1.0)) }) { s, _ ->
                    s.game.wage[Area.EDUCACAO.ordinal] *= 1.08; s.bump(Area.EDUCACAO, 2.0); s.shift(-1, 1.0)
                },
                EventOption("Pagar um abono único", { s, _ -> listOf(money(scaled(s, 25.0))) }) { s, _ -> s.pay(scaled(s, 25.0)) },
                EventOption("Cortar o ponto dos grevistas", { _, _ -> listOf(area(Area.EDUCACAO, -4.0), aprov(-5.0)) }) { s, _ -> s.bump(Area.EDUCACAO, -4.0); s.shift(-1, -5.0) },
            ),
        ),
        EventDef(
            "greve_saude", "strike", Crisis.GREVE, weight = { 0.6 },
            title = { _, _ -> "Médicos e enfermeiros param" },
            text = { _, _ -> "Os postos atendem só emergências. A categoria quer salário e equipamentos." },
            options = listOf(
                EventOption("Dar reajuste de 7%", { _, _ -> listOf(Chip("Saúde +7% no custo", false), area(Area.SAUDE, 2.0), aprov(1.0)) }) { s, _ ->
                    s.game.wage[Area.SAUDE.ordinal] *= 1.07; s.bump(Area.SAUDE, 2.0); s.shift(-1, 1.0)
                },
                EventOption("Comprar equipamentos", { s, _ -> listOf(money(scaled(s, 20.0)), area(Area.SAUDE, 1.0)) }) { s, _ -> s.pay(scaled(s, 20.0)); s.legacy(Area.SAUDE, 1.0) },
                EventOption("Ir à Justiça contra a greve", { _, _ -> listOf(area(Area.SAUDE, -4.0), aprov(-5.0)) }) { s, _ -> s.bump(Area.SAUDE, -4.0); s.shift(-1, -5.0) },
            ),
        ),
        EventDef(
            "dengue", "virus", Crisis.EPIDEMIA,
            title = { _, _ -> "Surto de dengue" },
            text = { _, _ -> "Os casos dobraram em duas semanas e as UPAs estão lotadas." },
            make = { s, _ -> PendingEvent("dengue", s.game.day, amount = scaled(s, 5.0)) },
            options = listOf(
                EventOption("Mutirão contra o mosquito", { _, e -> listOf(money(e.amount), area(Area.SAUDE, 1.0), aprov(2.0)) }) { s, e -> s.pay(e.amount); s.bump(Area.SAUDE, 1.0); s.shift(-1, 2.0) },
                EventOption("Campanha na TV e nas redes", { _, e -> listOf(money(e.amount * 0.3), area(Area.SAUDE, -1.0)) }) { s, e -> s.pay(e.amount * 0.3); s.bump(Area.SAUDE, -1.0) },
                EventOption("Deixar com o estado", { _, _ -> listOf(area(Area.SAUDE, -5.0), aprov(-5.0)) }) { s, _ -> s.bump(Area.SAUDE, -5.0); s.shift(-1, -5.0) },
            ),
        ),
        EventDef(
            "fabrica", "factory", null,
            title = { _, _ -> "Uma indústria quer vir para a cidade" },
            text = { _, _ -> "A empresa promete centenas de empregos, mas quer vantagens para se instalar aqui e não na cidade vizinha." },
            weight = { 0.8 },
            options = listOf(
                EventOption("Isentar de ISS por 5 anos", { _, _ -> listOf(area(Area.ECONOMIA, 5.0), Chip("Receita −2% por 5 anos", false)) }) { s, _ ->
                    s.legacy(Area.ECONOMIA, 5.0); s.addEffect("receita", 60, -0.02); s.addEffect("pib", 24, 0.01)
                },
                EventOption("Oferecer lote no distrito industrial",
                    { _, _ -> listOf(area(Area.ECONOMIA, 6.0)) },
                    enabled = { s, _ -> s.game.obras.any { it.done && it.type == "distrito" } },
                ) { s, _ -> s.legacy(Area.ECONOMIA, 6.0); s.addEffect("pib", 24, 0.012) },
                EventOption("Não dar nada", { _, _ -> listOf(Chip("A empresa vai embora", false)) }) { _, _ -> },
            ),
        ),
        EventDef(
            "buraco", "camera", null, level = Level.CIDADE,
            title = { s, e -> "Vídeo de cratera ${Fmt.inB(s.bname(e))} viraliza" },
            text = { _, _ -> "Um carro caiu num buraco e o vídeo já passa de um milhão de visualizações." },
            make = { s, r -> PendingEvent("buraco", s.game.day, bairro = pickBairro(s, r), amount = scaled(s, 0.8, 50_000.0, 6e6)) },
            options = listOf(
                EventOption("Tapa-buraco hoje mesmo", { _, e -> listOf(money(e.amount), aprov(3.0, " no bairro")) }) { s, e -> s.pay(e.amount); s.shift(e.bairro, 3.0) },
                EventOption("Responder nas redes", { _, _ -> listOf(aprov(-1.0)) }) { s, _ -> s.shift(-1, -1.0) },
                EventOption("Ignorar", { _, _ -> listOf(aprov(-5.0, " no bairro"), aprov(-2.0)) }) { s, e -> s.shift(e.bairro, -5.0); s.shift(-1, -2.0) },
            ),
        ),
        EventDef(
            "convenio", "handshake", null, level = Level.CIDADE,
            title = { s, _ -> "Governo ${Fmt.ofState(s.state.code, s.state.name)} oferece convênio" },
            text = { s, e -> "O estado paga 80% de ${Content.obra(e.text).name.lowercase()} ${Fmt.inB(s.bname(e))}. A prefeitura entra com o resto." },
            weight = { 0.7 },
            make = { s, r ->
                val t = Content.OBRAS.filter { it.cost <= 20e6 }.random(r)
                val b = s.game.bairros.indices.minBy { s.coverage(t.area, it) + r.nextDouble() * 1000 }
                PendingEvent("convenio", s.game.day, bairro = b, text = t.id)
            },
            options = listOf(
                EventOption("Aceitar", { s, e -> listOf(money(s.obraCost(Content.obra(e.text)) * 0.2), Chip("Obra nova", true)) }) { s, e -> s.build(e.text, e.bairro, 0.2) },
                EventOption("Recusar", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
        EventDef(
            "associacao", "people", null, level = Level.CIDADE,
            title = { s, e -> "Moradores ${Fmt.ofB(s.bname(e))} cobram a prefeitura" },
            text = { s, e -> "A associação de moradores quer melhorias em ${s.game.bairros[e.bairro].need.label.lowercase()}." },
            make = { s, r -> PendingEvent("associacao", s.game.day, bairro = pickBairro(s, r)) },
            options = listOf(
                EventOption("Prometer uma obra em 12 meses", { _, _ -> listOf(aprov(4.0, " no bairro"), Chip("−6 se não cumprir", false)) }) { s, e ->
                    s.shift(e.bairro, 4.0)
                    s.game.promises += Promise(e.bairro, s.game.bairros[e.bairro].need, s.game.day + 365)
                },
                EventOption("Dizer que não há verba", { _, _ -> listOf(aprov(-3.0, " no bairro")) }) { s, e -> s.shift(e.bairro, -3.0) },
            ),
        ),
        EventDef(
            "escandalo", "scandal", Crisis.ESCANDALO,
            title = { _, e -> "Denúncia contra ${e.text}" },
            text = { _, e -> "A imprensa revela contratos suspeitos na secretaria de ${e.area?.label?.lowercase()}." },
            weight = { 0.0 },
            options = listOf(
                EventOption("Demitir na hora", { _, _ -> listOf(aprov(-2.0), Chip("Secretaria vaga", false)) }) { s, e -> e.area?.let { s.fire(it) }; s.shift(-1, -2.0) },
                EventOption("Defender o secretário", { _, _ -> listOf(aprov(-7.0)) }) { s, _ -> s.shift(-1, -7.0) },
            ),
        ),
        EventDef(
            "crise", "chart_down", Crisis.CRISE_ECON,
            title = { _, _ -> "Crise econômica derruba a arrecadação" },
            text = { _, _ -> "O comércio vende menos e a receita própria deve cair 8% nos próximos 12 meses." },
            weight = { s -> if (s.game.modifiers.any { it.key == "receita" && it.value <= -0.05 }) 0.0 else 0.25 },
            options = listOf(
                EventOption("Cortar 10% em todas as áreas", { _, _ -> listOf(Chip("Receita −8% por 1 ano", false), aprov(-3.0)) }) { s, _ ->
                    s.addEffect("receita", 12, -0.08); s.addEffect("pib", 12, -0.02)
                    for (a in Area.entries) s.setSpend(a, s.game.spend[a.ordinal] * 0.9)
                    s.shift(-1, -3.0)
                },
                EventOption("Manter os gastos", { _, _ -> listOf(Chip("Receita −8% por 1 ano", false)) }) { s, _ -> s.addEffect("receita", 12, -0.08); s.addEffect("pib", 12, -0.02) },
            ),
        ),
        EventDef(
            "festival", "party", null,
            title = { _, _ -> "Produtores pedem apoio para um festival" },
            text = { _, _ -> "Três dias de música e comida regional na praça principal." },
            weight = { 0.7 },
            make = { s, _ -> PendingEvent("festival", s.game.day, amount = scaled(s, 1.5, 100_000.0, 25e6)) },
            options = listOf(
                EventOption("Apoiar", { _, e -> listOf(money(e.amount), area(Area.CULTURA, 1.5), area(Area.TURISMO, 1.5), aprov(2.0)) }) { s, e ->
                    s.pay(e.amount); s.legacy(Area.CULTURA, 1.5); s.legacy(Area.TURISMO, 1.5); s.shift(-1, 2.0)
                },
                EventOption("Não apoiar", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
        EventDef(
            "apagao", "bolt", Crisis.APAGAO,
            title = { s, e -> "Apagão deixa ${Fmt.theB(s.bname(e))} no escuro" },
            text = { _, _ -> "A distribuidora não diz quando a luz volta. Postos e escolas sem energia." },
            make = { s, r -> PendingEvent("apagao", s.game.day, bairro = pickBairro(s, r), amount = scaled(s, 1.0, 80_000.0, 8e6)) },
            options = listOf(
                EventOption("Alugar geradores", { _, e -> listOf(money(e.amount), aprov(2.0, " no bairro")) }) { s, e -> s.pay(e.amount); s.shift(e.bairro, 2.0) },
                EventOption("Cobrar a distribuidora em público", { _, _ -> listOf(aprov(1.0)) }) { s, _ -> s.shift(-1, 1.0) },
                EventOption("Esperar", { _, _ -> listOf(aprov(-4.0, " no bairro")) }) { s, e -> s.shift(e.bairro, -4.0) },
            ),
        ),
        EventDef(
            "onibus", "bus", null, level = Level.CIDADE,
            title = { _, _ -> "Empresa de ônibus pede reajuste" },
            text = { _, _ -> "Sem aumento da tarifa, ela ameaça cortar linhas nos bairros mais distantes." },
            options = listOf(
                EventOption("Aceitar o reajuste", { _, _ -> listOf(aprov(-5.0), area(Area.TRANSPORTE, 2.0)) }) { s, _ -> s.shift(-1, -5.0); s.legacy(Area.TRANSPORTE, 2.0) },
                EventOption("Subsidiar a tarifa", { _, _ -> listOf(Chip("Transporte +15% no gasto", false), aprov(1.0)) }) { s, _ ->
                    s.setSpend(Area.TRANSPORTE, s.game.spend[Area.TRANSPORTE.ordinal] + 0.15); s.shift(-1, 1.0)
                },
                EventOption("Negar", { _, _ -> listOf(area(Area.TRANSPORTE, -4.0)) }) { s, _ -> s.legacy(Area.TRANSPORTE, -2.0); s.bump(Area.TRANSPORTE, -2.0) },
            ),
        ),
        EventDef(
            "salario_camara", "gavel", null, level = Level.CIDADE,
            title = { _, _ -> "Câmara aprova aumento para os vereadores" },
            text = { _, _ -> "O projeto chega para sanção. A população acompanha pelas redes." },
            weight = { 0.5 },
            options = listOf(
                EventOption("Vetar", { _, _ -> listOf(aprov(3.0), Chip("Câmara −10", false)) }) { s, _ ->
                    s.shift(-1, 3.0); s.game.camara.forEach { it.favor -= 10; it.relation -= 10 }
                },
                EventOption("Sancionar", { _, _ -> listOf(aprov(-4.0), Chip("Câmara +6", true)) }) { s, _ ->
                    s.shift(-1, -4.0); s.game.camara.forEach { it.favor += 6; it.relation += 6 }
                },
            ),
        ),
        EventDef(
            "ataque", "camera", null,
            title = { _, e -> "${e.text} ataca você na TV" },
            text = { _, e -> "A propaganda do adversário mostra ${e.area?.the} da cidade como prova de que você fracassou." },
            weight = { 0.0 },
            options = listOf(
                EventOption("Responder com números", { s, _ -> listOf(money(Elections.adCost(s) * 0.5)) }) { s, _ ->
                    s.game.election?.let { it.fund = (it.fund - Elections.adCost(s) * 0.5).coerceAtLeast(0.0) }
                },
                EventOption("Processar por fake news", { _, _ -> listOf(Chip("Pode virar a seu favor… ou não", false)) }) { s, _ ->
                    s.game.election?.let { it.boost += if (s.rng(321).nextBoolean()) 1.0 else -1.0 }
                },
                EventOption("Ignorar", { _, _ -> listOf(Chip("Campanha −1,5", false)) }) { s, _ -> s.game.election?.let { it.boost -= 1.5 } },
            ),
        ),
        EventDef(
            "cpi", "gavel", null,
            title = { _, _ -> "Oposição abre CPI contra a prefeitura" },
            text = { _, _ -> "A maioria da Câmara quer investigar os contratos do seu governo." },
            weight = { 0.0 },
            options = listOf(
                EventOption("Colaborar com a investigação", { _, _ -> listOf(aprov(-2.0)) }) { s, _ -> s.shift(-1, -2.0) },
                EventOption("Negociar com o centrão", { _, _ -> listOf(Chip("+2 cargos comissionados", false), Chip("Independentes +15", true)) }) { s, _ ->
                    s.game.cargos += 2
                    s.game.camara.filter { Camara.stance(it) == 0 }.forEach { it.favor += 15; it.relation += 15 }
                },
                EventOption("Atacar a oposição nas redes", { _, _ -> listOf(aprov(1.0), Chip("Oposição −15", false)) }) { s, _ ->
                    s.shift(-1, 1.0); s.game.camara.filter { Camara.stance(it) < 0 }.forEach { it.favor -= 15; it.relation -= 15 }
                },
            ),
        ),
        EventDef(
            "cassacao", "gavel", null,
            title = { _, _ -> "Câmara vota sua cassação" },
            text = { s, _ -> "Com aprovação de ${s.game.approval.toInt()}%, a oposição juntou assinaturas. São precisos 2/3 dos vereadores para tirar você." },
            weight = { 0.0 },
            options = listOf(
                EventOption("Ir ao plenário se defender", { _, _ -> listOf(Chip("2/3 cassam", false)) }) { s, _ ->
                    if (Camara.impeach(s)) s.game.gameOver = GameOver("cassado", "Cassado", "A Câmara aprovou sua cassação. Você deixa a prefeitura hoje.")
                    else { s.news("check", "Cassação rejeitada: você segue no cargo", 1); s.shift(-1, 3.0) }
                },
                EventOption("Renunciar", { _, _ -> listOf(Chip("Fim do mandato", false)) }) { s, _ ->
                    s.game.gameOver = GameOver("renuncia", "Renúncia", "Você deixa a prefeitura antes da votação.")
                },
            ),
        ),
        EventDef(
            "greve_pm", "siren", Crisis.GREVE,
            title = { _, _ -> "Policiais militares cruzam os braços" },
            text = { _, _ -> "Sem reajuste há anos, a PM ameaça aquartelamento. Os comerciantes fecham as portas." },
            level = Level.ESTADO, weight = { 0.5 },
            options = listOf(
                EventOption("Dar reajuste de 10%", { _, _ -> listOf(Chip("Segurança +10% no custo", false), area(Area.SEGURANCA, 2.0)) }) { s, _ ->
                    s.game.wage[Area.SEGURANCA.ordinal] *= 1.10; s.bump(Area.SEGURANCA, 2.0)
                },
                EventOption("Pedir a Força Nacional", { _, _ -> listOf(aprov(-3.0), area(Area.SEGURANCA, -2.0)) }) { s, _ -> s.shift(-1, -3.0); s.bump(Area.SEGURANCA, -2.0) },
            ),
        ),
        EventDef(
            "rebeliao", "gavel", null,
            title = { s, e -> "Rebelião no presídio ${Fmt.ofB(s.bname(e))}" },
            text = { _, _ -> "Presos tomam um pavilhão e fazem reféns. As imagens estão em todos os jornais." },
            level = Level.ESTADO, weight = { s -> if (s.coverage(Area.SEGURANCA) > s.game.pop * 0.3) 0.3 else 0.7 },
            make = { s, r -> PendingEvent("rebeliao", s.game.day, bairro = pickBairro(s, r)) },
            options = listOf(
                EventOption("Negociar com calma", { _, _ -> listOf(aprov(-1.0)) }) { s, _ -> s.shift(-1, -1.0) },
                EventOption("Mandar o choque", { _, _ -> listOf(Chip("Arriscado", false)) }) { s, e ->
                    if (s.rng(55).nextDouble() < 0.6) { s.shift(-1, 2.0); s.news("check", "Rebelião contida sem mortes", 1, e.bairro) }
                    else { s.shift(-1, -6.0); s.news("alert", "Ação da polícia termina com mortos", 3, e.bairro) }
                },
            ),
        ),
        EventDef(
            "prefeito_pede", "handshake", null,
            title = { s, e -> "Prefeito ${Fmt.ofB(s.bname(e))} pede verba" },
            text = { s, e -> "${s.game.bairros[e.bairro].mayorName} (${s.partyOf(s.game.bairros[e.bairro].mayorParty).sigla}) quer ${Fmt.money(e.amount)} para uma obra na cidade." },
            level = Level.ESTADO, weight = { 0.0 },
            options = listOf(
                EventOption("Mandar o dinheiro", { _, e -> listOf(money(e.amount), Chip("Prefeito +25", true), aprov(3.0, " na cidade")) }) { s, e ->
                    s.pay(e.amount); s.game.bairros[e.bairro].mayorRel += 25; s.shift(e.bairro, 3.0)
                },
                EventOption("Dizer que não há verba", { _, _ -> listOf(Chip("Prefeito −10", false)) }) { s, e -> s.game.bairros[e.bairro].mayorRel -= 10 },
            ),
        ),
        EventDef(
            "lobby", "money", Crisis.ESCANDALO,
            title = { _, _ -> "Empresário oferece ajuda na campanha" },
            text = { _, _ -> "Em troca, ele quer seu voto num projeto que interessa à empresa dele." },
            who = 1, weight = { 0.6 },
            options = listOf(
                EventOption("Aceitar", { _, _ -> listOf(Chip("Capital +6", true), Chip("Risco de escândalo", false)) }) { s, _ ->
                    s.game.career.capital += 6
                    if (s.rng(66).nextDouble() < 0.35) { s.shift(-1, -8.0); s.news("scandal", "Imprensa revela acordo seu com empresário", 3) }
                },
                EventOption("Recusar", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
        EventDef(
            "entrevista", "speech", null,
            title = { _, _ -> "Programa de TV quer entrevistar você" },
            text = { s, _ -> "O assunto: o governo de ${s.game.execName} (${s.partyOf(s.game.execParty).sigla})." },
            who = 1,
            options = listOf(
                EventOption("Elogiar o governo", { s, _ -> listOf(Chip("Governo +10", true), aprov(if (s.game.execApproval >= 50) 1.0 else -1.0)) }) { s, _ ->
                    s.game.execRel += 10; s.shift(-1, if (s.game.execApproval >= 50) 1.0 else -1.0)
                },
                EventOption("Criticar o governo", { s, _ -> listOf(Chip("Governo −10", false), aprov(if (s.game.execApproval < 50) 2.0 else -1.0)) }) { s, _ ->
                    s.game.execRel -= 10; s.shift(-1, if (s.game.execApproval < 50) 2.0 else -1.0)
                },
                EventOption("Não ir", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
        EventDef(
            "convite_base", "handshake", null,
            title = { s, _ -> "O ${s.execTitle} convida você para a base" },
            text = { _, _ -> "Na base, suas emendas saem inteiras — mas você divide o desgaste do governo." },
            who = 1, weight = { s -> if (s.game.stance == 1) 0.0 else 0.5 },
            options = listOf(
                EventOption("Entrar na base", { _, _ -> listOf(Chip("Emendas pagas 100%", true)) }) { s, _ -> Legislativo.setStance(s, 1) },
                EventOption("Recusar", { _, _ -> listOf(Chip("Governo −5", false)) }) { s, _ -> s.game.execRel -= 5 },
            ),
        ),
        EventDef(
            "assaltos", "siren", null,
            title = { s, e -> "Onda de assaltos ${Fmt.inB(s.bname(e))}" },
            text = { _, _ -> "Comerciantes fecham mais cedo e pedem policiamento." },
            make = { s, r -> PendingEvent("assaltos", s.game.day, bairro = pickBairro(s, r), amount = scaled(s, 1.5, 100_000.0, 15e6)) },
            options = listOf(
                EventOption("Reforçar a Guarda Municipal", { _, e -> listOf(money(e.amount), aprov(4.0, " no bairro"), area(Area.SEGURANCA, 1.0)) }) { s, e ->
                    s.pay(e.amount); s.shift(e.bairro, 4.0); s.bump(Area.SEGURANCA, 1.0)
                },
                EventOption("Pedir a Polícia Militar ao estado", { _, _ -> listOf(aprov(1.0, " no bairro")) }) { s, e -> s.shift(e.bairro, 1.0) },
                EventOption("Esperar", { _, _ -> listOf(aprov(-6.0, " no bairro"), area(Area.SEGURANCA, -2.0)) }) { s, e -> s.shift(e.bairro, -6.0); s.bump(Area.SEGURANCA, -2.0) },
            ),
        ),
        EventDef(
            "emenda", "money", null, level = Level.CIDADE,
            title = { _, _ -> "Deputado oferece emenda parlamentar" },
            text = { _, e -> "Um deputado federal quer mandar ${Fmt.money(e.amount)} para a cidade. Em troca, espera seu apoio em 2030." },
            weight = { 0.6 },
            make = { s, _ -> PendingEvent("emenda", s.game.day, amount = scaled(s, 12.0, 500_000.0, 40e6)) },
            options = listOf(
                EventOption("Aceitar", { _, e -> listOf(money(e.amount, true)) }) { s, e -> s.game.cash += e.amount },
                EventOption("Recusar", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
        EventDef(
            "elogio", "trophy", null,
            title = { s, _ -> "Reportagem elogia a ${best(s).label.lowercase()} da cidade" },
            text = { s, _ -> "Um jornal do estado mostra ${s.city.name} como exemplo." },
            weight = { s -> if (s.game.ind.max() > 65) 0.5 else 0.0 },
            options = listOf(EventOption("Ótimo", { _, _ -> listOf(aprov(2.0)) }) { s, _ -> s.shift(-1, 2.0) }),
        ),
        EventDef(
            "eleicao_geral", "vote", null,
            title = { _, _ -> "Eleições gerais de 2030" },
            text = { s, _ -> "Seu partido quer você no palanque do candidato a governador ${Fmt.ofState(s.state.code, s.state.name)}. Disputar outros cargos chega numa próxima versão." },
            weight = { 0.0 },
            options = listOf(
                EventOption("Subir no palanque", { _, _ -> listOf(Chip("Mais convênios", true), aprov(-1.0)) }) { s, _ -> s.shift(-1, -1.0); s.addEffect("convenio", 24, 1.0) },
                EventOption("Ficar neutro", { _, _ -> emptyList() }) { _, _ -> },
            ),
        ),
    )

    private fun best(s: Sim) = Area.entries.maxBy { s.game.ind[it.ordinal] }

    fun def(key: String) = ALL.first { it.key == key }

    fun spawn(s: Sim) {
        val r = s.rng(77)
        val who = if (s.governs) 0 else 1
        val pool = ALL.filter { (it.crisis == null || it.crisis in s.game.settings.crises) && it.who == who && (it.level == null || it.level == s.level) }
            .map { d -> d to (d.weight(s) * (if (d.key == "convenio") 1 + 0.5 * s.game.modifiers.count { it.key == "convenio" && it.until > s.game.day } else 1.0)) }
            .filter { it.second > 0 }
        if (pool.isEmpty()) return
        var x = r.nextDouble() * pool.sumOf { it.second }
        val d = pool.firstOrNull { x -= it.second; x <= 0 }?.first ?: pool.last().first
        val ev = d.make(s, r)?.let { if (it.key.isEmpty()) PendingEvent(d.key, s.game.day, it.bairro, it.area, it.amount, it.text) else it } ?: return
        s.game.events += ev
    }

    fun resolve(s: Sim, option: Int) {
        val e = s.game.events.firstOrNull() ?: return
        val d = def(e.key)
        val o = d.options.getOrNull(option) ?: return
        if (!o.enabled(s, e)) return
        s.game.events.removeAt(0)
        o.apply(s, e)
        s.game.approval = s.game.bairros.sumOf { it.approval * it.popShare }
    }
}
