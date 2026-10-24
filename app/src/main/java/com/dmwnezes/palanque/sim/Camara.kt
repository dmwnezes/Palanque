package com.dmwnezes.palanque.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Lei municipal: efeito nas áreas (alcançado aos poucos), na aprovação, custo anual por habitante
 * e fator na receita do IPTU. econ/social = posição ideológica (−1 esquerda/progressista, +1 direita/conservadora).
 */
class LawDef(
    val id: String,
    val name: String,
    val desc: String,
    val econ: Float,
    val social: Float,
    val ind: Map<Area, Double> = emptyMap(),
    val aprov: Double = 0.0,
    val costPc: Double = 0.0,
    val iptuMul: Double = 1.0,
    val custom: Boolean = false,
)

object Laws {
    val READY = listOf(
        LawDef("tarifa_zero", "Tarifa zero no ônibus", "Ônibus de graça para todos, pago pela prefeitura", -0.6f, -0.2f,
            mapOf(Area.TRANSPORTE to 8.0, Area.ECONOMIA to 1.0), aprov = 3.0, costPc = 120.0),
        LawDef("iptu_progressivo", "IPTU progressivo", "Imóveis caros e terrenos vazios pagam mais", -0.6f, 0f, aprov = -1.0, iptuMul = 1.15),
        LawDef("iptu_idosos", "Isenção de IPTU para idosos", "Aposentados com um imóvel não pagam IPTU", 0f, 0.2f, aprov = 2.0, iptuMul = 0.92),
        LawDef("guarda_armada", "Guarda Municipal armada", "Agentes com porte de arma e mais patrulhas", 0.3f, 0.7f,
            mapOf(Area.SEGURANCA to 5.0), aprov = 1.0, costPc = 15.0),
        LawDef("escola_integral", "Escola em tempo integral", "Alunos da rede municipal ficam o dia todo na escola", -0.2f, 0f,
            mapOf(Area.EDUCACAO to 6.0), aprov = 1.0, costPc = 90.0),
        LawDef("ppp_saneamento", "PPP do saneamento", "Empresa privada investe no esgoto e cobra tarifa", 0.7f, 0f,
            mapOf(Area.SANEAMENTO to 7.0), aprov = -1.5),
        LawDef("coleta_seletiva", "Coleta seletiva obrigatória", "Lixo separado e cooperativas de catadores", -0.2f, -0.3f,
            mapOf(Area.AMBIENTE to 5.0), costPc = 20.0),
        LawDef("alvara_na_hora", "Alvará na hora", "Abrir empresa em um dia, sem papelada", 0.6f, 0f,
            mapOf(Area.ECONOMIA to 5.0)),
        LawDef("fundo_turismo", "Fundo municipal de turismo", "Eventos, divulgação e sinalização turística", 0f, 0f,
            mapOf(Area.TURISMO to 5.0, Area.CULTURA to 1.0), costPc = 15.0),
        LawDef("plano_diretor", "Novo plano diretor", "Regras para onde a cidade pode crescer", -0.1f, -0.1f,
            mapOf(Area.AMBIENTE to 3.0, Area.TRANSPORTE to 2.0, Area.ECONOMIA to -1.0)),
        LawDef("ficha_limpa", "Ficha limpa nos cargos", "Condenados não podem ocupar cargos na prefeitura", 0f, 0.1f, aprov = 2.0),
        LawDef("renda_municipal", "Renda básica municipal", "Auxílio mensal para as famílias mais pobres", -0.8f, -0.2f,
            mapOf(Area.ECONOMIA to 2.0, Area.SAUDE to 1.0), aprov = 4.0, costPc = 150.0),
        LawDef("escola_civico", "Escolas cívico-militares", "Gestão disciplinar com militares da reserva", 0.4f, 0.8f,
            mapOf(Area.EDUCACAO to 2.0, Area.SEGURANCA to 1.0), aprov = 0.5, costPc = 10.0),
        LawDef("cultura_periferia", "Cultura na periferia", "Editais para grupos culturais dos bairros", -0.4f, -0.6f,
            mapOf(Area.CULTURA to 6.0), aprov = 1.0, costPc = 12.0),        LawDef("apps_transporte", "Aplicativos de transporte regulamentados", "Uber e mototáxi com regras e uma taxa por corrida", 0.2f, 0f, mapOf(Area.TRANSPORTE to 2.0), costPc = -5.0),
        LawDef("fogos_silenciosos", "Fogos sem barulho", "Proíbe fogos com estampido por causa de animais e autistas", -0.1f, -0.4f, mapOf(Area.AMBIENTE to 1.0), aprov = 0.5),
        LawDef("rodizio", "Rodízio de carros", "Cada placa fica um dia da semana sem rodar no centro", 0f, -0.1f, mapOf(Area.TRANSPORTE to 3.0), aprov = -2.0),
        LawDef("zona_azul", "Zona azul digital", "Estacionamento pago pelo celular nas ruas do comércio", 0.4f, 0f, mapOf(Area.TRANSPORTE to 1.0), aprov = -1.0, costPc = -15.0),
        LawDef("hortas_urbanas", "Hortas em terrenos vazios", "Lotes abandonados viram hortas comunitárias", -0.3f, -0.3f, mapOf(Area.SAUDE to 1.0, Area.AMBIENTE to 1.0), costPc = 5.0),
        LawDef("toque_recolher", "Toque de recolher para menores", "Menores de 16 sem adulto fora de casa só até as 23h", 0.2f, 0.8f, mapOf(Area.SEGURANCA to 1.0), aprov = -0.5),
        LawDef("wifi_pracas", "Wi-Fi grátis nas praças", "Internet aberta nas praças e terminais", -0.1f, 0f, mapOf(Area.CULTURA to 1.0), aprov = 1.0, costPc = 6.0),
        LawDef("lei_silencio", "Lei do silêncio mais dura", "Multa alta para som alto depois das 22h", 0.1f, 0.5f, mapOf(Area.AMBIENTE to 1.0), aprov = -0.5),
        LawDef("feira_fds", "Feira livre nos fins de semana", "Ruas fechadas para feira e comércio de rua", 0.1f, 0f, mapOf(Area.ECONOMIA to 2.0), aprov = 1.0, costPc = 3.0),
        LawDef("cota_mulheres", "Cota para mulheres nos cargos", "Metade dos cargos de chefia da prefeitura para mulheres", -0.3f, -0.7f),
        LawDef("escola_sem_celular", "Escola sem celular", "Celular guardado durante as aulas da rede municipal", 0f, 0.3f, mapOf(Area.EDUCACAO to 2.0), aprov = 1.0),
        LawDef("protecao_animal", "Proteção animal e castração gratuita", "Castração, vacina e multa para maus-tratos", -0.1f, -0.2f, mapOf(Area.SAUDE to 1.0), aprov = 2.0, costPc = 8.0),
        LawDef("food_trucks", "Food trucks liberados", "Comida de rua com licença simples", 0.5f, 0f, mapOf(Area.ECONOMIA to 1.0, Area.TURISMO to 1.0)),
    )

    /** Leis estaduais (Assembleia). */
    val ESTADO = listOf(
        LawDef("icms_cesta", "ICMS zero na cesta básica", "Comida mais barata; o estado arrecada menos", -0.3f, 0f,
            mapOf(Area.ECONOMIA to 2.0), aprov = 3.0, costPc = 60.0),
        LawDef("pm_escolas", "Polícia nas escolas", "Ronda escolar da PM em todo o estado", 0.3f, 0.6f,
            mapOf(Area.SEGURANCA to 3.0, Area.EDUCACAO to 1.0), aprov = 1.0, costPc = 20.0),
        LawDef("camera_pm", "Câmeras nas fardas da PM", "Gravação das abordagens policiais", -0.4f, -0.6f,
            mapOf(Area.SEGURANCA to 2.0), aprov = 0.5, costPc = 8.0),
        LawDef("ensino_medio_integral", "Ensino médio integral", "Todas as escolas estaduais em tempo integral", -0.2f, 0f,
            mapOf(Area.EDUCACAO to 6.0), aprov = 1.0, costPc = 70.0),
        LawDef("privatiza_agua", "Privatização da companhia de água", "Empresa privada assume o saneamento", 0.8f, 0f,
            mapOf(Area.SANEAMENTO to 8.0), aprov = -2.0, costPc = -30.0),
        LawDef("incentivo_industria", "Incentivo fiscal à indústria", "Desconto de ICMS para quem gerar empregos", 0.6f, 0f,
            mapOf(Area.ECONOMIA to 6.0), aprov = 0.5, costPc = 40.0),
        LawDef("passe_intermunicipal", "Passe livre intermunicipal", "Estudantes viajam de graça entre cidades", -0.5f, -0.2f,
            mapOf(Area.TRANSPORTE to 4.0, Area.EDUCACAO to 1.0), aprov = 2.0, costPc = 25.0),
        LawDef("desmatamento_zero", "Desmatamento zero", "Fiscalização e multas pesadas", -0.4f, -0.4f,
            mapOf(Area.AMBIENTE to 7.0, Area.ECONOMIA to -2.0), costPc = 10.0),        LawDef("bilhete_unico", "Bilhete único metropolitano", "Uma passagem para ônibus, metrô e trem da região", -0.5f, -0.2f, mapOf(Area.TRANSPORTE to 4.0), aprov = 2.0, costPc = 40.0),
        LawDef("militariza_escolas", "Militarização das escolas estaduais", "PM na gestão das escolas de ensino médio", 0.4f, 0.9f, mapOf(Area.SEGURANCA to 1.0), costPc = 10.0),
        LawDef("privatiza_rodovias", "Privatização das rodovias", "Concessão com obras e pedágio", 0.8f, 0f, mapOf(Area.TRANSPORTE to 3.0), aprov = -2.0, costPc = -20.0),
        LawDef("taxa_lixo", "Taxa do lixo", "Cobrança para coleta e destino do lixo", 0.4f, 0f, mapOf(Area.SANEAMENTO to 1.0), aprov = -3.0, costPc = -25.0),
        LawDef("bolsa_estudante", "Bolsa estudante do ensino médio", "Pagamento mensal para o aluno não largar a escola", -0.6f, -0.2f, mapOf(Area.EDUCACAO to 3.0), aprov = 1.5, costPc = 30.0),
        LawDef("delegacia_online", "Delegacia online", "Boletim de ocorrência pelo celular", 0f, 0f, mapOf(Area.SEGURANCA to 1.0), costPc = 2.0),
        LawDef("isencao_ipva", "Isenção de IPVA para motos e carros velhos", "Quem tem veículo popular deixa de pagar", -0.2f, 0.2f, aprov = 3.0, iptuMul = 0.85),
        LawDef("icms_luz", "Corte do ICMS da conta de luz", "Energia mais barata; o estado arrecada menos", -0.3f, 0f, mapOf(Area.ECONOMIA to 1.0), aprov = 4.0, costPc = 60.0),
        LawDef("credito_rural", "Crédito para pequenos produtores", "Juros baixos para a agricultura familiar", -0.5f, -0.1f, mapOf(Area.ECONOMIA to 2.0), costPc = 20.0),
        LawDef("concurso_pm", "Concurso para a PM", "Milhares de novos policiais nas ruas", 0.2f, 0.4f, mapOf(Area.SEGURANCA to 3.0), aprov = 1.0, costPc = 35.0),
        LawDef("antibullying", "Lei antibullying", "Programa contra o bullying nas escolas", -0.2f, -0.5f, mapOf(Area.EDUCACAO to 1.0)),
        LawDef("servico_ambiental", "Pagamento por serviço ambiental", "Produtor ganha para manter a mata em pé", -0.5f, -0.5f, mapOf(Area.AMBIENTE to 4.0, Area.ECONOMIA to -1.0), costPc = 15.0),
    )

    /** Leis federais (Câmara dos Deputados): valem para todo o país, inclusive o seu estado. */
    val FEDERAL = listOf(
        LawDef("reforma_tributaria", "Isenção de IR até R$ 5 mil", "Quem ganha pouco deixa de pagar Imposto de Renda", -0.4f, 0f,
            mapOf(Area.ECONOMIA to 3.0), aprov = 4.0, costPc = 130.0),
        LawDef("arcabouco", "Teto de gastos mais duro", "Corta despesas para reduzir a dívida pública", 0.8f, 0f,
            mapOf(Area.ECONOMIA to 3.0, Area.SAUDE to -2.0, Area.EDUCACAO to -2.0), aprov = -2.0, costPc = -250.0),
        LawDef("piso_enfermagem", "Piso da enfermagem", "Salário mínimo nacional para enfermeiros", -0.5f, -0.1f,
            mapOf(Area.SAUDE to 3.0), aprov = 2.0, costPc = 40.0),
        LawDef("pec_seguranca", "PEC da segurança pública", "Polícia Federal e estados integrados contra o crime", 0.3f, 0.5f,
            mapOf(Area.SEGURANCA to 4.0), aprov = 2.0, costPc = 25.0),
        LawDef("marco_saneamento", "Novo marco do saneamento", "Metas de esgoto e concessões privadas", 0.6f, 0f,
            mapOf(Area.SANEAMENTO to 5.0), aprov = 0.5),
        LawDef("escala_6x1", "Fim da escala 6x1", "Dois dias de folga por semana para todos", -0.7f, -0.2f,
            mapOf(Area.ECONOMIA to -2.0), aprov = 4.0),
        LawDef("porte_armas", "Ampliar porte de armas", "Mais facilidade para o cidadão andar armado", 0.5f, 0.9f,
            mapOf(Area.SEGURANCA to 1.0), aprov = -1.0),
        LawDef("bolsa_familia", "Bolsa Família maior", "Aumenta o valor pago às famílias pobres", -0.8f, -0.2f,
            mapOf(Area.ECONOMIA to 2.0, Area.SAUDE to 1.0), aprov = 3.0, costPc = 160.0),
        LawDef("privatiza_estatais", "Privatizar estatais", "Venda de empresas de energia e correios", 0.9f, 0f,
            mapOf(Area.ECONOMIA to 3.0), aprov = -2.0, costPc = -60.0),
        LawDef("mais_medicos", "Mais Médicos ampliado", "Médicos contratados para o interior", -0.4f, -0.1f,
            mapOf(Area.SAUDE to 4.0), aprov = 2.0, costPc = 30.0),        LawDef("reforma_previdencia", "Reforma da Previdência", "Idade mínima maior e regras mais duras para aposentar", 0.8f, 0.2f, mapOf(Area.ECONOMIA to 2.0), aprov = -5.0, costPc = -400.0),
        LawDef("reforma_administrativa", "Reforma administrativa", "Fim da estabilidade para novos servidores", 0.8f, 0f, mapOf(Area.ECONOMIA to 1.0), aprov = -2.0, costPc = -150.0),
        LawDef("taxar_fortunas", "Imposto sobre grandes fortunas", "Quem tem mais de R$ 10 milhões paga um imposto anual", -0.9f, -0.2f, mapOf(Area.ECONOMIA to -1.0), aprov = 2.0, costPc = -100.0),
        LawDef("imposto_minimo", "Imposto mínimo para super-ricos", "Alíquota mínima de IR para quem ganha muito", -0.6f, 0f, aprov = 3.0, costPc = -60.0),
        LawDef("bc_autonomo", "Autonomia do Banco Central", "Diretoria com mandato fixo, longe do governo", 0.8f, 0f, mapOf(Area.ECONOMIA to 2.0)),
        LawDef("voto_impresso", "Voto impresso", "Urna eletrônica imprime o voto para conferência", 0.3f, 0.8f, aprov = -0.5, costPc = 2.0),
        LawDef("cannabis_medicinal", "Cannabis medicinal", "Plantio e venda de remédios à base de cannabis", -0.3f, -0.8f, mapOf(Area.SAUDE to 1.0)),
        LawDef("maioridade_penal", "Redução da maioridade penal", "Jovens de 16 anos respondem como adultos por crimes graves", 0.3f, 0.9f, mapOf(Area.SEGURANCA to 1.0), aprov = 1.0),
        LawDef("lei_fake_news", "Lei das fake news", "Redes sociais respondem pelo que é espalhado", -0.3f, -0.4f, aprov = -0.5),
        LawDef("liberdade_economica", "Liberdade econômica total", "Menos regras para abrir e fechar empresas e contratar", 0.9f, 0.2f, mapOf(Area.ECONOMIA to 3.0, Area.SAUDE to -1.0), aprov = -1.0),
        LawDef("salario_minimo", "Salário mínimo com ganho real", "Aumento acima da inflação todo ano", -0.7f, -0.1f, mapOf(Area.ECONOMIA to 1.0), aprov = 4.0, costPc = 120.0),
        LawDef("renda_basica", "Renda básica universal", "Um valor mensal para cada brasileiro adulto", -0.9f, -0.3f, mapOf(Area.ECONOMIA to 2.0, Area.SAUDE to 1.0), aprov = 5.0, costPc = 500.0),
        LawDef("moradia_nacional", "Programa nacional de moradia", "Casas com água e esgoto para famílias pobres", -0.6f, -0.2f, mapOf(Area.SANEAMENTO to 3.0), aprov = 2.0, costPc = 80.0),
        LawDef("isencao_igrejas", "Fim da isenção para igrejas", "Templos passam a pagar impostos", -0.4f, -0.8f, aprov = -2.0, costPc = -10.0),
        LawDef("privatiza_petrobras", "Privatizar a Petrobras", "Venda do controle da estatal do petróleo", 0.95f, 0f, mapOf(Area.ECONOMIA to 1.0), aprov = -3.0, costPc = -100.0),
        LawDef("anistia", "Anistia política", "Perdão a condenados por atos contra a democracia", 0.6f, 1.0f, aprov = -2.0),
        LawDef("cotas_ampliadas", "Cotas ampliadas nas universidades", "Mais vagas para alunos de escola pública e negros", -0.5f, -0.7f, mapOf(Area.EDUCACAO to 1.0)),
        LawDef("plano_fronteiras", "Plano nacional de fronteiras", "Exército e PF contra o tráfico de armas e drogas", 0.4f, 0.5f, mapOf(Area.SEGURANCA to 3.0), aprov = 1.0, costPc = 20.0),
    )

    private val FEDERAL_IDS by lazy { FEDERAL.map { it.id }.toSet() }
    fun isFederal(id: String) = id in FEDERAL_IDS

    /** Leis que a casa de um cargo pode votar. */
    fun forRole(r: Role) = when {
        r.governor == Role.PRESIDENTE -> FEDERAL
        r.level == Level.ESTADO -> ESTADO
        else -> READY
    }

    val INTENSITY = listOf("Pequena", "Média", "Grande")

    fun fromCustom(c: CustomLaw, level: Level = Level.CIDADE): LawDef {
        val k = c.intensity.coerceIn(1, 3)
        val gain = listOf(3.0, 5.0, 8.0)[k - 1]
        val pays = c.funding == 0
        return LawDef(
            c.id, c.name,
            "${c.area.label} +${gain.toInt()} · " + if (pays) "a prefeitura paga" else "paga com nova taxa",
            c.ideo, 0f, mapOf(c.area to gain),
            aprov = if (pays) 0.6 * k else -1.0 * k,
            costPc = if (pays) c.area.ref(level) * listOf(0.05, 0.1, 0.18)[k - 1] else 0.0,
            custom = true,
        )
    }

    private val ALL by lazy { (READY + ESTADO + FEDERAL).associateBy { it.id } }

    fun def(g: Game, id: String): LawDef = ALL[id] ?: fromCustom(g.customLaws.first { it.id == id }, g.level)
}

/**
 * Casa legislativa da jurisdição (Câmara Municipal, Assembleia Legislativa ou Câmara dos Deputados):
 * membros, relação com você, votações e negociação.
 */
object Camara {
    /** Bancada federal de cada estado (deputados federais). */
    val FEDERAIS = mapOf(
        12 to 8, 27 to 9, 16 to 8, 13 to 8, 29 to 39, 23 to 22, 53 to 8, 32 to 10, 52 to 17, 21 to 18, 51 to 8, 50 to 8, 31 to 53,
        15 to 17, 25 to 12, 41 to 30, 26 to 25, 22 to 10, 33 to 46, 24 to 8, 43 to 31, 11 to 8, 14 to 8, 42 to 16, 35 to 70, 28 to 8, 17 to 8,
    )

    /** Deputados estaduais: o triplo da bancada federal até 36, depois um a mais por federal acima de 12 (art. 27). */
    fun assembleia(uf: Int): Int { val f = FEDERAIS[uf] ?: 8; return if (f <= 12) 3 * f else 36 + (f - 12) }

    /** Tamanho da casa do cargo. */
    fun houseSize(g: Game): Int = when {
        g.role == Role.DEP_FEDERAL || g.level == Level.PAIS -> 513
        g.role == Role.SENADOR -> 81
        g.level == Level.ESTADO -> assembleia(g.uf)
        else -> seats(g.pop)
    }

    /** Vagas disputadas pelo cargo na sua jurisdição (para a eleição proporcional). */
    fun constituencySeats(r: Role, g: Game): Int = when (r) {
        Role.DEP_FEDERAL -> FEDERAIS[g.uf] ?: 8
        Role.DEP_ESTADUAL -> assembleia(g.uf)
        else -> seats(g.pop)
    }

    /** Número de vereadores pela população (Constituição, art. 29, IV). */
    fun seats(pop: Double): Int {
        val limits = listOf(15e3, 30e3, 50e3, 80e3, 120e3, 160e3, 300e3, 450e3, 600e3, 750e3, 900e3, 1.05e6, 1.2e6, 1.35e6, 1.5e6,
            1.8e6, 2.4e6, 3e6, 4e6, 5e6, 6e6, 7e6, 8e6)
        val i = limits.indexOfFirst { pop <= it }.let { if (it < 0) limits.size else it }
        return 9 + 2 * i
    }

    fun ideoDistance(e1: Float, s1: Float, e2: Float, s2: Float) = (abs(e1 - e2) + abs(s1 - s2)) / 2.0

    /** Para onde a relação de um membro com você caminha. */
    fun target(s: Sim, v: Vereador): Double {
        val g = s.game
        val p = s.party
        if (!s.governs) {
            // parlamentar ou candidato: afinidade, capital político e favores
            val same = if (v.party == p.id) 35.0 else 0.0
            val gov = if (v.party == g.execParty) g.stance * 10.0 else 0.0
            return (same - 45 * ideoDistance(v.econ, v.social, p.econ, p.social) + 10 + g.career.capital * 0.3 + gov + v.favor).coerceIn(-100.0, 100.0)
        }
        val same = if (v.party == p.id) 35.0 else 0.0
        val dist = ideoDistance(v.econ, v.social, p.econ, p.social)
        val sec = if (g.secretarios.any { it?.party == v.party }) 12.0 else 0.0
        val reduto = s.localBonus(v.bairro) * 1.5 + (g.bairros[v.bairro].approval - 50) * 0.3
        val cargo = if (v.hasCargo) 15.0 else 0.0
        return (same - 45 * dist + 10 + (g.approval - 50) * 0.6 + sec + reduto + cargo + v.favor).coerceIn(-100.0, 100.0)
    }

    fun stance(v: Vereador) = when {
        v.relation >= 20 -> 1 // base
        v.relation <= -20 -> -1 // oposição
        else -> 0
    }

    /** Monta a Câmara: cadeiras divididas pelo tamanho dos partidos; o seu partido tem bancada própria. */
    fun elect(s: Sim, playerShare: Double = 0.15) {
        val g = s.game
        val r = s.rng(555 + g.termNumber)
        val n = houseSize(g)
        val parties = g.parties.filter { !it.custom || it.id == g.player.party }
        g.camara.clear()
        g.cargos = 0
        val mine = (n * playerShare.coerceIn(0.05, 0.45)).toInt().coerceAtLeast(1)
        // o Congresso do presidente tem também os 81 senadores
        val senators = if (g.level == Level.PAIS) 81 else 0
        val mineSen = (senators * playerShare.coerceIn(0.05, 0.45)).toInt()
        repeat(n + senators) { k ->
            val sen = k >= n
            val i = if (sen) k - n else k
            val party = if (i < (if (sen) mineSen else mine)) s.party else {
                val others = parties.filter { it.id != g.player.party }
                val tot = others.sumOf { it.size.toDouble() }
                var x = r.nextDouble() * tot
                others.firstOrNull { x -= it.size; x <= 0 } ?: others.last()
            }
            val b = r.nextInt(g.bairros.size)
            val v = Vereador(
                g.nextId++, Content.personName(r), party.id,
                (party.econ + (r.nextFloat() - 0.5f) * 0.4f).coerceIn(-1f, 1f),
                (party.social + (r.nextFloat() - 0.5f) * 0.4f).coerceIn(-1f, 1f),
                b, 0.0, senator = sen,
            )
            v.relation = (target(s, v) + (r.nextDouble() - 0.5) * 20).coerceIn(-100.0, 100.0)
            g.camara += v
        }
    }

    fun monthly(s: Sim) {
        val g = s.game
        if (g.camara.isEmpty()) elect(s)
        for (v in g.camara) {
            v.favor *= 0.9
            v.relation += (target(s, v) - v.relation) * 0.12
        }
        if (!s.governs || g.regime) return
        // CPI quando a oposição domina
        val opp = g.camara.count { stance(it) < 0 }
        if (g.settings.randomEvents && opp * 2 > g.camara.size && g.events.isEmpty() && s.rng(808).nextDouble() < 0.05)
            g.events += PendingEvent("cpi", g.day)
        // cassação: governo impopular e oposição com 2/3
        if (g.approval < 22 && opp * 3 >= g.camara.size * 2 && g.events.none { it.key == "cassacao" } && s.rng(909).nextDouble() < 0.25)
            g.events += PendingEvent("cassacao", g.day)
    }

    /** Relação de um membro com o governo da IA (para os projetos do governo). */
    fun execRelation(s: Sim, v: Vereador): Double {
        val g = s.game
        val ep = s.partyOf(g.execParty)
        return (if (v.party == ep.id) 40.0 else 0.0) - 45 * ideoDistance(v.econ, v.social, ep.econ, ep.social) + 10 + (g.execApproval - 50) * 0.6
    }

    /** Lei com custo, efeitos e ideologia — chance de cada membro votar a favor. */
    fun chance(s: Sim, v: Vereador, law: LawDef, revoke: Boolean, author: String = "player"): Double {
        val d = ideoDistance(v.econ, v.social, law.econ, law.social)
        val fit = if (revoke) d - 0.6 else 0.6 - d
        val rel = if (author == "exec" && !s.governs) execRelation(s, v) else v.relation
        val score = rel * 0.6 + 35 * fit + (s.game.execApproval - 50) * 0.4
        return 1 / (1 + exp(-score / 12))
    }

    class Count(val yes: Int, val no: Int, val maybe: Int)

    fun count(s: Sim, b: Bill): Count = count(s, Laws.def(s.game, b.law), b.revoke, b.author)

    fun count(s: Sim, law: LawDef, revoke: Boolean, author: String = "player"): Count {
        var y = 0; var n = 0; var m = 0
        for (v in s.game.camara) {
            val p = chance(s, v, law, revoke, author)
            when { p >= 0.65 -> y++; p <= 0.35 -> n++; else -> m++ }
        }
        return Count(y, n, m)
    }

    /** Seu projeto em votação. */
    fun pending(s: Sim): Bill? = s.game.bills.firstOrNull { it.status == "tramitando" && it.author == "player" }
    /** Projeto do governo em votação (quando você é parlamentar). */
    fun execBill(s: Sim): Bill? = s.game.bills.firstOrNull { it.status == "tramitando" && it.author == "exec" }

    /** Maioria simples da casa (contando você, quando é membro). */
    fun majority(s: Sim) = (s.game.camara.size + (if (s.legislates) 1 else 0)) / 2 + 1

    /** Envia um projeto; a votação acontece em 30 dias. */
    fun send(s: Sim, lawId: String, revoke: Boolean = false): String? {
        val g = s.game
        if (g.regime) {
            // Congresso fechado: a lei sai por decreto
            if (revoke) g.laws.remove(lawId) else if (lawId !in g.laws) g.laws += lawId
            s.news("gavel", "Decreto: ${if (revoke) "revogada" else "em vigor"} \"${Laws.def(g, lawId).name}\"", 1)
            return null
        }
        if (pending(s) != null) return "Já há um projeto seu em votação"
        if (!revoke && lawId in g.laws) return "Essa lei já está em vigor"
        g.bills += Bill(g.nextId++, lawId, g.day, g.day + 30, revoke)
        val name = Laws.def(g, lawId).name
        s.news("gavel", if (revoke) "Projeto para revogar \"$name\" chega à ${g.role.house}" else "Projeto \"$name\" chega à ${g.role.house}", 0)
        return null
    }

    fun withdraw(s: Sim) {
        val b = pending(s) ?: return
        b.status = "retirado"
        s.news("gavel", "Projeto retirado da Câmara", 0)
    }

    fun vote(s: Sim, b: Bill) {
        val g = s.game
        val law = Laws.def(g, b.law)
        val r = s.rng(b.id)
        var yes = 0
        var yesSen = 0
        for (v in g.camara) if (r.nextDouble() < chance(s, v, law, b.revoke, b.author)) { yes++; if (v.senator) yesSen++ }
        var total = g.camara.size
        // você também vota quando é membro da casa
        if (s.legislates) { total++; if (b.playerVote == 1) yes++ }
        b.yes = yes; b.no = total - yes
        // no Congresso, precisa passar nas duas casas
        val sen = g.camara.count { it.senator }
        val ok = if (sen > 0) (yes - yesSen) * 2 > total - sen && yesSen * 2 > sen else yes * 2 > total
        b.status = if (ok) "aprovado" else "rejeitado"
        if (ok) { if (b.revoke) g.laws.remove(b.law) else g.laws += b.law }
        val house = houseName(g)
        s.news(if (ok) "check" else "broken", (if (ok) "$house aprova" else "$house rejeita") + " \"${law.name}\": $yes a ${b.no}", if (ok) 1 else 2)
        Legislativo.afterVote(s, b, ok)
    }

    // ───────────── negociação (uma de cada por projeto) ─────────────

    fun emendaCost(s: Sim) = if (s.legislates) Legislativo.emendaBudget(s) * 0.1 else max(150_000.0, s.game.pop * (if (s.level == Level.CIDADE) 1.5 else 0.15))
    fun cargoCost(s: Sim) = max(90_000.0, s.game.pop * (if (s.level == Level.CIDADE) 0.6 else 0.06))

    fun talk(s: Sim, v: Vereador, bill: Int): String? {
        if (v.talked == bill) return "Vocês já conversaram sobre este projeto"
        v.talked = bill; v.favor += 6; v.relation = (v.relation + 6).coerceAtMost(100.0)
        return null
    }

    fun fund(s: Sim, v: Vereador, bill: Int): String? {
        if (v.funded == bill) return "Já liberou emenda para ele neste projeto"
        val c = emendaCost(s)
        if (s.legislates) {
            // parlamentar paga com as próprias emendas
            if (s.game.emendasLeft < c) return "Faltam emendas (${Fmt.money(c)})"
            s.game.emendasLeft -= c
        } else {
            if (s.game.cash < c) return "Falta caixa (${Fmt.money(c)})"
            s.game.cash -= c
        }
        v.funded = bill; v.favor += 18; v.relation = (v.relation + 18).coerceAtMost(100.0)
        return null
    }

    fun cargo(s: Sim, v: Vereador): String? {
        if (v.hasCargo) return "Ele já indicou alguém para a prefeitura"
        v.hasCargo = true; s.game.cargos++; v.relation = (v.relation + 25).coerceAtMost(100.0)
        s.shift(-1, -0.5)
        return null
    }

    fun promise(s: Sim, v: Vereador): String? {
        val g = s.game
        if (v.promised >= g.day - 365) return "Já há uma obra prometida no reduto dele"
        v.promised = g.day; v.favor += 15; v.relation = (v.relation + 15).coerceAtMost(100.0)
        g.promises += Promise(v.bairro, g.bairros[v.bairro].need, g.day + 365, vereador = v.id)
        return null
    }

    /** Nome da casa para as notícias. */
    fun houseName(g: Game) = when {
        g.level == Level.PAIS -> "Congresso"
        g.role == Role.DEP_FEDERAL -> "Câmara dos Deputados"
        g.role == Role.SENADOR -> "Senado"
        g.level == Level.ESTADO -> "Assembleia"
        else -> "Câmara"
    }

    /** Votação da cassação: 2/3 da Câmara tiram o prefeito. */
    fun impeach(s: Sim): Boolean {
        val g = s.game
        val r = s.rng(4040)
        val yes = g.camara.count { v -> r.nextDouble() < when (stance(v)) { -1 -> 0.85; 0 -> 0.4; else -> 0.05 } }
        return yes * 3 >= g.camara.size * 2
    }
}
