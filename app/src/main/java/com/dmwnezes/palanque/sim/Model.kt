package com.dmwnezes.palanque.sim

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Mandatos municipais começam em 1º de janeiro de 2029. */
val START_DATE: LocalDate = LocalDate.of(2029, 1, 1)

/** Áreas de governo. `ref` = gasto anual de referência por habitante (R$) num município médio. */
enum class Area(val label: String, val short: String, val ref: Double, val weight: Double) {
    SAUDE("Saúde", "Saúde", 950.0, 0.20),
    EDUCACAO("Educação", "Educação", 1050.0, 0.17),
    SEGURANCA("Segurança", "Segurança", 70.0, 0.16),
    ECONOMIA("Economia e emprego", "Economia", 60.0, 0.16),
    TRANSPORTE("Transporte", "Transporte", 180.0, 0.08),
    SANEAMENTO("Saneamento", "Saneamento", 140.0, 0.08),
    CULTURA("Cultura e lazer", "Cultura", 45.0, 0.05),
    AMBIENTE("Meio ambiente", "Ambiente", 50.0, 0.06),
    TURISMO("Turismo", "Turismo", 35.0, 0.04);

    companion object { val REF_TOTAL = entries.sumOf { it.ref } }
}

enum class Role(val label: String, val playable: Boolean, val desc: String) {
    VEREADOR("Vereador", false, "Câmara Municipal · negocia e vota"),
    PREFEITO("Prefeito", true, "Governa a cidade"),
    DEP_ESTADUAL("Deputado estadual", false, "Assembleia Legislativa"),
    GOVERNADOR("Governador", false, "Governa o estado"),
    DEP_FEDERAL("Deputado federal", false, "Câmara dos Deputados"),
    SENADOR("Senador", false, "Senado Federal · 8 anos"),
    PRESIDENTE("Presidente", false, "Governa o país"),
}

/** Tipos de crise que podem ser desligados no novo jogo. */
enum class Crisis(val label: String) {
    ENCHENTE("Enchentes"), SECA("Secas"), GREVE("Greves"), EPIDEMIA("Epidemias"),
    CRISE_ECON("Crises econômicas"), ESCANDALO("Escândalos"), APAGAO("Apagões"),
}

/** Partido: ideologia em dois eixos (−1 esquerda/progressista, +1 direita/conservador). */
@Serializable
data class Party(
    val id: String,
    val sigla: String,
    val name: String,
    val number: Int,
    val color: Long,
    val econ: Float,
    val social: Float,
    val size: Float,
    val custom: Boolean = false,
)

@Serializable
data class Politician(val name: String, val party: String)

@Serializable
class Settings(val crises: MutableSet<Crisis> = Crisis.entries.toMutableSet(), val seed: Long = 2029L)

/** Estado de cada bairro (a geometria é gerada de novo a partir do código da cidade). */
@Serializable
class BairroState(
    val name: String,
    var popShare: Double,
    var approval: Double,
    /** área que mais preocupa os moradores */
    val need: Area,
    /** alegria passageira de uma inauguração */
    var bump: Double = 0.0,
    /** comícios feitos nesta campanha */
    var rallies: Int = 0,
)

@Serializable
class Secretario(
    val id: Int,
    val name: String,
    val area: Area,
    val party: String,
    /** 1 a 5 */
    val competence: Int,
    val honesty: Int,
    val loyalty: Int,
    var monthsInOffice: Int = 0,
)

/** Obra pública. progress: 0..1; done quando inaugurada. */
@Serializable
class Obra(
    val id: Int,
    val type: String,
    val bairro: Int,
    val startDay: Int,
    var progress: Double = 0.0,
    var done: Boolean = false,
    var paid: Double = 0.0,
    /** feita com dinheiro de convênio (paga só a contrapartida) */
    val share: Double = 1.0,
)

@Serializable
class PendingEvent(
    val key: String,
    val day: Int,
    val bairro: Int = -1,
    val area: Area? = null,
    val amount: Double = 0.0,
    val text: String = "",
)

@Serializable
class FeedItem(val day: Int, val icon: String, val text: String, val tone: Int = 0, val bairro: Int = -1)

@Serializable
class MonthRecord(val day: Int, val approval: Double, val cash: Double, val revenue: Double, val expense: Double, val pop: Double)

/** Efeito passageiro (ex.: receita menor numa crise). */
@Serializable
class TimedEffect(val key: String, val until: Int, val value: Double, val area: Area? = null)

/** Promessa feita a um bairro: obra de um tipo até uma data. */
@Serializable
class Promise(val bairro: Int, val area: Area, val until: Int, var kept: Boolean = false)

/** Totais do ano corrente, para conferir pisos e Lei de Responsabilidade Fiscal. */
@Serializable
class YearTotals(
    var revenue: Double = 0.0,
    var taxBase: Double = 0.0,
    var health: Double = 0.0,
    var education: Double = 0.0,
    var personnel: Double = 0.0,
    var borrowed: Double = 0.0,
    var months: Int = 0,
)

@Serializable
class Candidate(val name: String, val party: String, var strength: Double, val player: Boolean = false)

@Serializable
class Election(
    val day: Int,
    val runoffDay: Int,
    val candidates: MutableList<Candidate>,
    val polls: MutableList<List<Double>> = mutableListOf(),
    var round: Int = 0,
    /** votos do 1º turno por bairro e candidato (fração) */
    var result: List<List<Double>> = emptyList(),
    var totals: List<Double> = emptyList(),
    var runoff: List<Double> = emptyList(),
    var finished: Boolean = false,
    var won: Boolean = false,
    var seen: Boolean = false,
    /** verba de campanha (R$) */
    var fund: Double = 0.0,
    /** propaganda na TV e nas redes: pontos somados em toda a cidade */
    var boost: Double = 0.0,
    /** índice do adversário no 2º turno */
    var rival2: Int = -1,
)

@Serializable
class GameOver(val kind: String, val title: String, val text: String)

@Serializable
class Game(
    val settings: Settings,
    val cityCode: Int,
    val player: Politician,
    val parties: MutableList<Party>,
    var role: Role = Role.PREFEITO,
    var day: Int = 0,
    val bairros: MutableList<BairroState> = mutableListOf(),
    /** indicador de cada área, 0..100 */
    val ind: DoubleArray = DoubleArray(Area.entries.size),
    /** ponto de partida real de cada área (herança da cidade) */
    val base: DoubleArray = DoubleArray(Area.entries.size),
    /** nível de gasto em cada área (1 = referência) */
    val spend: DoubleArray = DoubleArray(Area.entries.size) { 1.0 },
    /** bônus permanente de eventos em cada área */
    val legacy: DoubleArray = DoubleArray(Area.entries.size),
    var pop: Double = 0.0,
    var pib: Double = 0.0,
    /** fator do IPTU (1 = normal) */
    var iptu: Double = 1.0,
    /** alíquota do ISS, de 2% a 5% */
    var iss: Double = 3.0,
    /** escala de gasto por habitante da cidade (custo local) */
    var costScale: Double = 1.0,
    /** PIB per capita no início: salários e contratos acompanham a economia */
    var pibPc0: Double = 0.0,
    /** reajustes acumulados de salário por área */
    val wage: DoubleArray = DoubleArray(Area.entries.size) { 1.0 },
    var cash: Double = 0.0,
    var debt: Double = 0.0,
    var approval: Double = 50.0,
    val obras: MutableList<Obra> = mutableListOf(),
    val secretarios: MutableList<Secretario?> = MutableList(Area.entries.size) { null },
    val pool: MutableList<Secretario> = mutableListOf(),
    var nextId: Int = 1,
    val events: MutableList<PendingEvent> = mutableListOf(),
    var nextEventDay: Int = 20,
    val feed: MutableList<FeedItem> = mutableListOf(),
    val history: MutableList<MonthRecord> = mutableListOf(),
    val modifiers: MutableList<TimedEffect> = mutableListOf(),
    val promises: MutableList<Promise> = mutableListOf(),
    var year: YearTotals = YearTotals(),
    var lastMonth: MonthRecord? = null,
    var termNumber: Int = 1,
    /** dia em que o mandato atual começou */
    var termStart: Int = 0,
    /** salários atrasados no último fechamento */
    var late: Boolean = false,
    var tceWarnings: Int = 0,
    var rejected: Int = 0,
    var election: Election? = null,
    var gameOver: GameOver? = null,
)
