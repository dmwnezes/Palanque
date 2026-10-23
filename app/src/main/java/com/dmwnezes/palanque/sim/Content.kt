package com.dmwnezes.palanque.sim

/** Obra do catálogo: custo em R$, prazo em meses, quantas pessoas atende. */
class ObraType(val id: String, val name: String, val area: Area, val cost: Double, val months: Int, val serves: Double, val icon: String, val desc: String, val level: Level = Level.CIDADE)

object Content {
    /** Partidos com registro no TSE (tamanho e posição aproximados, só para o jogo). */
    val PARTIES = listOf(
        Party("pl", "PL", "Partido Liberal", 22, 0xFF1F4FA3, 0.7f, 0.8f, 1.0f),
        Party("pt", "PT", "Partido dos Trabalhadores", 13, 0xFFD62828, -0.7f, -0.5f, 0.95f),
        Party("uniao", "UNIÃO", "União Brasil", 44, 0xFF2563EB, 0.5f, 0.3f, 0.8f),
        Party("psd", "PSD", "Partido Social Democrático", 55, 0xFFF2A007, 0.2f, 0.1f, 0.85f),
        Party("mdb", "MDB", "Movimento Democrático Brasileiro", 15, 0xFF1F9D55, 0.1f, 0.1f, 0.85f),
        Party("pp", "PP", "Progressistas", 11, 0xFF3B82F6, 0.5f, 0.5f, 0.8f),
        Party("rep", "REPUBLICANOS", "Republicanos", 10, 0xFF0EA5E9, 0.5f, 0.7f, 0.75f),
        Party("psb", "PSB", "Partido Socialista Brasileiro", 40, 0xFFF59E0B, -0.4f, -0.3f, 0.65f),
        Party("pdt", "PDT", "Partido Democrático Trabalhista", 12, 0xFFDC2626, -0.4f, -0.2f, 0.5f),
        Party("psdb", "PSDB", "Partido da Social Democracia Brasileira", 45, 0xFF2B6CB0, 0.3f, 0.0f, 0.45f),
        Party("pode", "PODE", "Podemos", 20, 0xFF16A34A, 0.3f, 0.2f, 0.4f),
        Party("psol", "PSOL", "Partido Socialismo e Liberdade", 50, 0xFFFACC15, -0.9f, -0.9f, 0.35f),
        Party("avante", "AVANTE", "Avante", 70, 0xFF7C3AED, 0.2f, 0.3f, 0.3f),
        Party("solid", "SOLIDARIEDADE", "Solidariedade", 77, 0xFFEA580C, 0.0f, 0.1f, 0.25f),
        Party("pcdob", "PCdoB", "Partido Comunista do Brasil", 65, 0xFFB91C1C, -0.9f, -0.5f, 0.25f),
        Party("pv", "PV", "Partido Verde", 43, 0xFF22C55E, -0.3f, -0.4f, 0.2f),
        Party("cidadania", "CIDADANIA", "Cidadania", 23, 0xFFEC4899, 0.0f, -0.3f, 0.2f),
        Party("novo", "NOVO", "Partido Novo", 30, 0xFFF97316, 0.95f, 0.3f, 0.25f),
        Party("rede", "REDE", "Rede Sustentabilidade", 18, 0xFF14B8A6, -0.3f, -0.6f, 0.15f),
        Party("prd", "PRD", "Partido Renovação Democrática", 25, 0xFF1D4ED8, 0.4f, 0.5f, 0.2f),
        Party("mobiliza", "MOBILIZA", "Mobiliza", 33, 0xFF0D9488, 0.1f, 0.2f, 0.12f),
        Party("agir", "AGIR", "Agir", 36, 0xFF65A30D, 0.3f, 0.4f, 0.1f),
        Party("dc", "DC", "Democracia Cristã", 27, 0xFF0369A1, 0.4f, 0.8f, 0.1f),
    )

    /** Cores para quem fundar o próprio partido. */
    val PARTY_COLORS = listOf(0xFFE3B341, 0xFF6CC28F, 0xFF5FB3A6, 0xFF7C9CF0, 0xFFE5604F, 0xFFB07CE8, 0xFFF08CC0, 0xFFF0E9D8)

    val OBRAS = listOf(
        ObraType("ubs", "Posto de saúde (UBS)", Area.SAUDE, 2.8e6, 10, 15000.0, "health", "Atendimento básico perto de casa"),
        ObraType("upa", "UPA 24 horas", Area.SAUDE, 9e6, 14, 60000.0, "emergency", "Urgência sem ir ao hospital"),
        ObraType("hospital", "Hospital municipal", Area.SAUDE, 45e6, 24, 150000.0, "hospital", "Internação e cirurgias"),
        ObraType("creche", "Creche", Area.EDUCACAO, 3.5e6, 9, 4000.0, "child", "Vaga para os pequenos, mães no trabalho"),
        ObraType("escola", "Escola de ensino fundamental", Area.EDUCACAO, 7e6, 12, 8000.0, "school", "Mil alunos em tempo integral"),
        ObraType("escola_tec", "Escola técnica", Area.EDUCACAO, 14e6, 16, 30000.0, "tech", "Curso profissional e emprego"),
        ObraType("base_gm", "Base da Guarda Municipal", Area.SEGURANCA, 1.8e6, 6, 25000.0, "shield", "Ronda e presença no bairro"),
        ObraType("cameras", "Videomonitoramento", Area.SEGURANCA, 2.5e6, 4, 20000.0, "camera", "Câmeras ligadas à central"),
        ObraType("iluminacao", "Iluminação em LED", Area.SEGURANCA, 1.5e6, 3, 12000.0, "light", "Rua clara é rua segura"),
        ObraType("mercado", "Mercado público", Area.ECONOMIA, 5e6, 8, 25000.0, "store", "Feira, comércio e renda local"),
        ObraType("distrito", "Distrito industrial", Area.ECONOMIA, 12e6, 12, 60000.0, "factory", "Terreno e estrutura para fábricas"),
        ObraType("pavimentacao", "Pavimentação", Area.TRANSPORTE, 6e6, 6, 8000.0, "road", "Asfalto e calçadas"),
        ObraType("terminal", "Terminal de ônibus", Area.TRANSPORTE, 15e6, 14, 60000.0, "bus", "Integração das linhas"),
        ObraType("ciclovia", "Ciclovia", Area.TRANSPORTE, 3e6, 5, 15000.0, "bike", "Ir de bicicleta com segurança"),
        ObraType("esgoto", "Rede de esgoto", Area.SANEAMENTO, 18e6, 18, 15000.0, "water", "Esgoto coletado em cada casa"),
        ObraType("ete", "Estação de tratamento", Area.SANEAMENTO, 35e6, 24, 100000.0, "plant", "Esgoto tratado antes do rio"),
        ObraType("drenagem", "Drenagem contra alagamentos", Area.SANEAMENTO, 8e6, 10, 20000.0, "rain", "Chuva forte sem rua alagada"),
        ObraType("praca", "Praça com academia", Area.CULTURA, 1.2e6, 4, 6000.0, "park", "Lazer e esporte ao ar livre"),
        ObraType("cultural", "Centro cultural", Area.CULTURA, 7e6, 12, 40000.0, "theater", "Biblioteca, teatro e oficinas"),
        ObraType("ginasio", "Ginásio poliesportivo", Area.CULTURA, 6e6, 10, 25000.0, "sport", "Quadras para escolas e times"),
        ObraType("parque", "Parque urbano", Area.AMBIENTE, 9e6, 12, 30000.0, "tree", "Área verde e caminhada"),
        ObraType("ecoponto", "Ecoponto", Area.AMBIENTE, 1e6, 3, 20000.0, "recycle", "Coleta seletiva e entulho"),
        ObraType("orla", "Polo turístico", Area.TURISMO, 12e6, 12, 60000.0, "beach", "Orla, mirante ou centro histórico"),
        ObraType("eventos", "Centro de eventos", Area.TURISMO, 18e6, 16, 80000.0, "stage", "Feiras, shows e congressos"),
    )

    /** Obras do governo estadual: maiores, mais caras, atendem regiões inteiras. */
    val OBRAS_ESTADO = listOf(
        ObraType("hosp_regional", "Hospital regional", Area.SAUDE, 70e6, 24, 400_000.0, "hospital", "Alta complexidade para a região", Level.ESTADO),
        ObraType("policlinica", "Policlínica estadual", Area.SAUDE, 18e6, 12, 150_000.0, "emergency", "Especialistas e exames", Level.ESTADO),
        ObraType("escola_medio", "Escola de ensino médio", Area.EDUCACAO, 14e6, 14, 20_000.0, "school", "Ensino médio em tempo integral", Level.ESTADO),
        ObraType("campus", "Campus da universidade estadual", Area.EDUCACAO, 90e6, 30, 600_000.0, "tech", "Graduação perto de casa", Level.ESTADO),
        ObraType("batalhao", "Batalhão da Polícia Militar", Area.SEGURANCA, 16e6, 10, 150_000.0, "shield", "Mais viaturas e policiais", Level.ESTADO),
        ObraType("delegacia", "Delegacia regional", Area.SEGURANCA, 6e6, 8, 80_000.0, "siren", "Polícia Civil e investigação", Level.ESTADO),
        ObraType("presidio", "Presídio", Area.SEGURANCA, 90e6, 30, 1_200_000.0, "gavel", "Vagas no sistema prisional", Level.ESTADO),
        ObraType("polo_industrial", "Polo industrial", Area.ECONOMIA, 45e6, 18, 500_000.0, "factory", "Infraestrutura para indústrias", Level.ESTADO),
        ObraType("rodovia", "Duplicação de rodovia", Area.TRANSPORTE, 130e6, 24, 400_000.0, "road", "Estrada segura entre cidades", Level.ESTADO),
        ObraType("metro", "Metrô ou VLT", Area.TRANSPORTE, 700e6, 48, 1_500_000.0, "bus", "Transporte de massa na capital", Level.ESTADO),
        ObraType("adutora", "Adutora", Area.SANEAMENTO, 95e6, 24, 300_000.0, "water", "Água do açude até as cidades", Level.ESTADO),
        ObraType("ete_estadual", "Saneamento regional", Area.SANEAMENTO, 75e6, 24, 500_000.0, "plant", "Esgoto tratado em várias cidades", Level.ESTADO),
        ObraType("teatro", "Centro cultural estadual", Area.CULTURA, 35e6, 18, 800_000.0, "theater", "Teatro, museu e biblioteca", Level.ESTADO),
        ObraType("parque_estadual", "Parque estadual", Area.AMBIENTE, 15e6, 12, 300_000.0, "tree", "Área protegida e trilhas", Level.ESTADO),
        ObraType("aeroporto", "Aeroporto regional", Area.TURISMO, 95e6, 30, 700_000.0, "stage", "Voos para o interior", Level.ESTADO),
        ObraType("convencoes", "Centro de convenções", Area.TURISMO, 60e6, 24, 1_000_000.0, "stage", "Feiras e congressos nacionais", Level.ESTADO),
    )

    fun obrasFor(level: Level) = if (level == Level.CIDADE) OBRAS else OBRAS_ESTADO

    fun obra(id: String) = OBRAS.firstOrNull { it.id == id } ?: OBRAS_ESTADO.first { it.id == id }

    private val FIRST = listOf(
        "Ana", "Maria", "Francisca", "Antônia", "Adriana", "Juliana", "Márcia", "Fernanda", "Patrícia", "Aline", "Sandra", "Camila",
        "Cláudia", "Luciana", "Raimunda", "Beatriz", "Tereza", "Helena", "Larissa", "Joana", "Simone", "Rita", "Vanessa", "Débora",
        "José", "João", "Antônio", "Francisco", "Carlos", "Paulo", "Pedro", "Lucas", "Luiz", "Marcos", "Rafael", "Daniel", "Raimundo",
        "Sérgio", "Eduardo", "Fábio", "Roberto", "Ricardo", "Gustavo", "André", "Fernando", "Jorge", "Rodrigo", "Tiago", "Cícero", "Edson",
    )
    private val LAST = listOf(
        "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves", "Pereira", "Lima", "Gomes", "Costa", "Ribeiro",
        "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa", "Rocha", "Dias", "Nascimento", "Andrade",
        "Moreira", "Nunes", "Marques", "Machado", "Mendes", "Freitas", "Cardoso", "Ramos", "Teixeira", "Araújo", "Cavalcante", "Bezerra",
        "Holanda", "Pinheiro", "Maia", "Sampaio", "Brito", "Farias", "Queiroz", "Tavares", "Menezes", "Coelho", "Batista", "Monteiro",
    )

    fun personName(r: kotlin.random.Random) = "${FIRST.random(r)} ${LAST.random(r)}"

    private val BAIRRO_NAMES = listOf(
        "Jardim das Flores", "Vila Esperança", "Parque Primavera", "Boa Vista", "Planalto", "Aurora", "Bela Vista", "Santo Antônio",
        "São José", "Monte Castelo", "Distrito Industrial", "Novo Horizonte", "Paraíso", "Lagoa Seca", "Cajueiro", "Mangueiral",
        "Alvorada", "Bom Jesus", "Conjunto Esperança", "Vila Nova", "Jardim América", "Alto da Serra", "Barra", "Pedra Branca",
        "São Francisco", "Nossa Senhora de Fátima", "Parque Dois Irmãos", "Conjunto Habitacional", "Cidade Nova", "Jardim Iracema",
        "Vila União", "Alto Alegre", "Morada do Sol", "Recanto Verde", "Jardim Tropical", "Santa Luzia", "Vila Operária", "Cruzeiro",
        "Jardim Bandeirantes", "Lagoa Redonda", "Vila Rica", "Parque Universitário", "Jardim Europa", "Água Fria", "Campo Grande",
        "Mirante", "Vila do Sol", "Coqueiral", "Riacho Doce", "Sítio Novo", "Nova Brasília", "Pirambu", "Itapuã", "Vila Velha",
    )
    private val RURAL = listOf("Zona Rural Norte", "Zona Rural Sul", "Zona Rural Leste", "Zona Rural Oeste", "Distrito de Serra Verde", "Distrito de Várzea Alegre")

    /** Nomes dos bairros: o primeiro é sempre o Centro; células grandes e afastadas viram zona rural. */
    fun bairroNames(r: kotlin.random.Random, n: Int, rural: List<Boolean>): List<String> {
        val pool = BAIRRO_NAMES.shuffled(r).toMutableList()
        val rur = RURAL.shuffled(r).toMutableList()
        return List(n) { i ->
            when {
                i == 0 -> "Centro"
                rural.getOrElse(i) { false } && rur.isNotEmpty() -> rur.removeAt(0)
                else -> pool.removeAt(0)
            }
        }
    }
}
