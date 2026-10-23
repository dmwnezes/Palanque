package com.dmwnezes.palanque.sim

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Mandatos municipais começam em 1º de janeiro de 2029. */
val START_DATE: LocalDate = LocalDate.of(2029, 1, 1)

/**
 * Áreas de governo. `ref` = gasto anual de referência por habitante (R$) num município médio;
 * `refState` = o mesmo para um governo estadual (polícia e ensino médio pesam mais).
 */
enum class Area(val label: String, val short: String, val ref: Double, val weight: Double, val refState: Double) {
    SAUDE("Saúde", "Saúde", 950.0, 0.20, 650.0),
    EDUCACAO("Educação", "Educação", 1050.0, 0.17, 900.0),
    SEGURANCA("Segurança", "Segurança", 70.0, 0.16, 450.0),
    ECONOMIA("Economia e emprego", "Economia", 60.0, 0.16, 60.0),
    TRANSPORTE("Transporte", "Transporte", 180.0, 0.08, 150.0),
    SANEAMENTO("Saneamento", "Saneamento", 140.0, 0.08, 80.0),
    CULTURA("Cultura e lazer", "Cultura", 45.0, 0.05, 30.0),
    AMBIENTE("Meio ambiente", "Ambiente", 50.0, 0.06, 40.0),
    TURISMO("Turismo", "Turismo", 35.0, 0.04, 25.0);

    fun ref(level: Level) = if (level == Level.CIDADE) ref else refState

    /** "a saúde" / "o transporte" */
    val the get() = (if (this in listOf(TRANSPORTE, SANEAMENTO, AMBIENTE, TURISMO)) "o " else "a ") + label.lowercase()

    companion object { fun refTotal(level: Level) = entries.sumOf { it.ref(level) } }
}

/** Onde o cargo atua: uma cidade (com bairros) ou um estado (com municípios). */
enum class Level { CIDADE, ESTADO }

/**
 * Cargos. municipal = eleito nas eleições municipais (2032, 2036…); os outros nas gerais (2030, 2034…).
 * house = casa legislativa do nível; members = como se chamam os membros.
 */
enum class Role(
    val label: String, val playable: Boolean, val desc: String, val level: Level, val executive: Boolean, val municipal: Boolean,
    val house: String, val members: String,
) {
    VEREADOR("Vereador", true, "Câmara Municipal · vota e manda emendas", Level.CIDADE, false, true, "Câmara Municipal", "vereadores"),
    PREFEITO("Prefeito", true, "Governa a cidade", Level.CIDADE, true, true, "Câmara Municipal", "vereadores"),
    DEP_ESTADUAL("Deputado estadual", true, "Assembleia Legislativa", Level.ESTADO, false, false, "Assembleia Legislativa", "deputados"),
    GOVERNADOR("Governador", true, "Governa o estado", Level.ESTADO, true, false, "Assembleia Legislativa", "deputados"),
    DEP_FEDERAL("Deputado federal", true, "Câmara dos Deputados, eleito pelo estado", Level.ESTADO, false, false, "Câmara dos Deputados", "deputados"),
    SENADOR("Senador", false, "Senado Federal · 8 anos", Level.ESTADO, false, false, "Senado Federal", "senadores"),
    PRESIDENTE("Presidente", false, "Governa o país", Level.ESTADO, true, false, "Congresso Nacional", "parlamentares");

    /** cargo executivo do mesmo nível (quem governa enquanto você legisla) */
    val governor get() = if (level == Level.CIDADE) PREFEITO else if (this == DEP_FEDERAL) PRESIDENTE else GOVERNADOR
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
class Settings(
    val crises: MutableSet<Crisis> = Crisis.entries.toMutableSet(),
    val seed: Long = 2029L,
    /** eventos aleatórios ligados (pode mudar no menu de pausa) */
    var randomEvents: Boolean = true,
    /** 0 fácil, 1 normal, 2 difícil: força dos adversários e quociente (pode mudar no menu de pausa) */
    var difficulty: Int = 1,
)

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
    /** apoio construído com emendas e obras do mandato legislativo (cai devagar) */
    var local: Double = 0.0,
    /** só nos estados: prefeito do município e relação com você */
    var mayorName: String = "",
    var mayorParty: String = "",
    var mayorRel: Double = 0.0,
)

/** Um cargo já exercido. */
@Serializable
class CareerEntry(val role: Role, val place: String, val from: Int, val to: Int, val approval: Double)

/** Carreira: o que passa de um cargo para outro. */
@Serializable
class Career(
    /** cidade onde a carreira começou (disputa os cargos municipais aqui) */
    val homeCity: Int,
    val history: MutableList<CareerEntry> = mutableListOf(),
    /** inelegível até este ano */
    var ineligibleUntil: Int = 0,
    var capital: Double = 20.0,
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
class Promise(val bairro: Int, val area: Area, val until: Int, var kept: Boolean = false, val vereador: Int = -1)

/** Vereador: partido, ideologia, reduto eleitoral e relação com o prefeito (−100 a +100). */
@Serializable
class Vereador(
    val id: Int,
    val name: String,
    val party: String,
    val econ: Float,
    val social: Float,
    /** bairro onde tem mais votos */
    val bairro: Int,
    var relation: Double,
    /** favores recebidos (emenda, cargo, obra prometida), que somem com o tempo */
    var favor: Double = 0.0,
    /** último projeto em que já houve conversa, emenda ou cargo */
    var talked: Int = -1,
    var funded: Int = -1,
    var hasCargo: Boolean = false,
    var promised: Int = -1,
)

/** Lei criada pelo jogador. intensity: 1 pequena, 2 média, 3 grande; funding: 0 prefeitura paga, 1 nova taxa. */
@Serializable
class CustomLaw(val id: String, val name: String, val area: Area, val intensity: Int, val ideo: Float, val funding: Int)

/** Projeto de lei na Câmara. */
@Serializable
class Bill(
    val id: Int,
    val law: String,
    val sentDay: Int,
    val voteDay: Int,
    /** revogar uma lei em vigor em vez de criar */
    val revoke: Boolean = false,
    /** "player" ou "exec" (projeto do governo, quando você é parlamentar) */
    val author: String = "player",
    /** seu voto quando você é parlamentar: 1 sim, −1 não, 0 abstenção */
    var playerVote: Int = 0,
    var status: String = "tramitando",
    var yes: Int = 0,
    var no: Int = 0,
)

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
    /** 0 = pré-campanha (convenção), 1 = campanha nas ruas */
    var phase: Int = 1,
    var conventionDone: Boolean = true,
    val viceOptions: MutableList<Vice> = mutableListOf(),
    var vice: Vice? = null,
    /** partidos coligados com você */
    val partners: MutableList<String> = mutableListOf(),
    /** fatia do horário eleitoral na TV (0..1) */
    var tv: Double = 0.2,
    /** debate em andamento: número (1, 2 ou 3 = 2º turno), pergunta atual e pontos */
    var debate: Int = 0,
    var debateStep: Int = 0,
    var debateScore: Double = 0.0,
    val debateAreas: MutableList<Area> = mutableListOf(),
    val debatesDone: MutableList<Int> = mutableListOf(),
    /** intenção de voto em você por bairro, da última pesquisa */
    var pollBairro: List<Double> = emptyList(),
    /** cargo em disputa */
    val office: Role = Role.PREFEITO,
    /** eleição proporcional (vereador e deputados): totals = [seus votos, quociente] */
    val proportional: Boolean = false,
)

/** Candidato a vice: partido, bairro onde é conhecido e fama (1 a 5). */
@Serializable
class Vice(val name: String, val party: String, val bairro: Int, val fame: Int)

/** Promessa de campanha: subir uma área em 5 pontos até o prazo. */
@Serializable
class CampaignPromise(val area: Area, val base: Double, val due: Int, var done: Boolean = false)

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
    /** último mês em que o Tribunal de Contas avisou do risco de rejeição */
    var tceAlertDay: Int = -999,
    var rejected: Int = 0,
    var election: Election? = null,
    var gameOver: GameOver? = null,
    val camara: MutableList<Vereador> = mutableListOf(),
    val bills: MutableList<Bill> = mutableListOf(),
    /** leis em vigor (ids de Laws.READY ou de customLaws) */
    val laws: MutableList<String> = mutableListOf(),
    val customLaws: MutableList<CustomLaw> = mutableListOf(),
    /** cargos comissionados dados a vereadores */
    var cargos: Int = 0,
    val campaignPromises: MutableList<CampaignPromise> = mutableListOf(),
    // ── carreira e níveis ──
    val level: Level = Level.CIDADE,
    /** estado da jurisdição (nas cidades, o estado da cidade) */
    val uf: Int = cityCode / 100000,
    val career: Career = Career(cityCode),
    /** false = sem mandato (candidato ou esperando a próxima eleição) */
    var inOffice: Boolean = true,
    /** cargo que está disputando na eleição deste ano */
    var running: Role? = null,
    /** escolha de candidatura aberta (20 de julho dos anos de eleição) */
    var candidacyPending: Boolean = false,
    /** mandato acabou e não há cargo: escolher quando voltar */
    var gapPending: Boolean = false,
    // ── mandato legislativo ──
    /** 1 base do governo, 0 independente, −1 oposição */
    var stance: Int = 0,
    var execName: String = "",
    var execParty: String = "",
    /** relação do governo (prefeito, governador ou presidente) com você */
    var execRel: Double = 0.0,
    /** aprovação do governo quando não é você quem governa */
    var execApproval: Double = 50.0,
    /** relação com o seu partido (−100 a 100) */
    var partyRel: Double = 50.0,
    /** emendas ainda disponíveis neste ano */
    var emendasLeft: Double = 0.0,
    var lastSpeech: Int = -999,
    var lastCpi: Int = -999,
    val emendas: MutableList<EmendaLog> = mutableListOf(),
)

/** Emenda parlamentar mandada para uma região. */
@Serializable
class EmendaLog(val day: Int, val unit: Int, val area: Area, val amount: Double)
