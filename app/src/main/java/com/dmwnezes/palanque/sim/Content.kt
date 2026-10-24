package com.dmwnezes.palanque.sim

/** Obra do catálogo: custo em R$, prazo em meses, quantas pessoas atende. */
class ObraType(
    val id: String, val name: String, val area: Area, val cost: Double, val months: Int, val serves: Double, val icon: String, val desc: String,
    val level: Level = Level.CIDADE,
    /** efeito permanente em outras áreas ao inaugurar (ex.: refinaria: economia +3, ambiente −2) */
    val extra: Map<Area, Double> = emptyMap(),
    /** manutenção por ano, em fração do custo */
    val upkeep: Double = 0.06,
    /** vizinhos que não querem a obra perto: aprovação perdida na região ao inaugurar */
    val nimby: Double = 0.0,
)

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
        ObraType("caps", "Centro de Atenção Psicossocial", Area.SAUDE, 4e6, 10, 30000.0, "health", "Saúde mental e dependência química", extra = mapOf(Area.SEGURANCA to 1.0)),
        ObraType("maternidade", "Clínica da mulher e maternidade", Area.SAUDE, 12e6, 16, 60000.0, "child", "Pré-natal, parto e saúde da mulher"),
        ObraType("farmacia", "Farmácia popular", Area.SAUDE, 1.5e6, 4, 20000.0, "health", "Remédio de graça ou barato"),
        ObraType("zoonoses", "Centro de zoonoses", Area.SAUDE, 3e6, 8, 80000.0, "tree", "Vacina e castração de animais", extra = mapOf(Area.AMBIENTE to 1.0)),
        ObraType("biblioteca", "Biblioteca de bairro", Area.EDUCACAO, 2e6, 6, 15000.0, "school", "Livros, internet e reforço escolar", extra = mapOf(Area.CULTURA to 1.0)),
        ObraType("bilingue", "Escola bilíngue", Area.EDUCACAO, 9e6, 14, 6000.0, "school", "Inglês desde a alfabetização"),
        ObraType("transp_escolar", "Transporte escolar rural", Area.EDUCACAO, 2.5e6, 3, 6000.0, "bus", "Ônibus para quem mora longe da escola", extra = mapOf(Area.TRANSPORTE to 1.0)),
        ObraType("botao_panico", "Botão do pânico para mulheres", Area.SEGURANCA, 1e6, 3, 40000.0, "alert", "Alerta direto para a Guarda"),
        ObraType("mediacao", "Centro de mediação de conflitos", Area.SEGURANCA, 1.2e6, 5, 30000.0, "handshake", "Briga de vizinho resolvida sem polícia"),
        ObraType("cerco_digital", "Cerco digital com leitura de placas", Area.SEGURANCA, 6e6, 6, 80000.0, "camera", "Câmeras nas entradas da cidade"),
        ObraType("incubadora", "Incubadora de startups", Area.ECONOMIA, 4e6, 10, 20000.0, "tech", "Escritório e mentoria para empresas novas"),
        ObraType("feira_empreendedor", "Feira do empreendedor", Area.ECONOMIA, 1.5e6, 4, 15000.0, "store", "Crédito e vitrine para pequenos negócios"),
        ObraType("qualificacao", "Centro de qualificação profissional", Area.ECONOMIA, 6e6, 10, 25000.0, "tech", "Cursos rápidos para arrumar emprego", extra = mapOf(Area.EDUCACAO to 1.0)),
        ObraType("viaduto", "Viaduto", Area.TRANSPORTE, 25e6, 18, 80000.0, "road", "Fim do gargalo no cruzamento"),
        ObraType("faixa_onibus", "Faixa exclusiva de ônibus", Area.TRANSPORTE, 4e6, 4, 60000.0, "bus", "Ônibus mais rápido que carro"),
        ObraType("bike_share", "Bicicletas compartilhadas", Area.TRANSPORTE, 2e6, 3, 30000.0, "bike", "Estações de aluguel pela cidade", extra = mapOf(Area.AMBIENTE to 1.0)),
        ObraType("teleferico", "Teleférico", Area.TRANSPORTE, 60e6, 24, 50000.0, "bus", "Liga o morro ao centro", extra = mapOf(Area.TURISMO to 1.0)),
        ObraType("aterro", "Aterro sanitário", Area.SANEAMENTO, 20e6, 14, 300000.0, "recycle", "Fecha o lixão", extra = mapOf(Area.AMBIENTE to 2.0), nimby = 5.0),
        ObraType("cisternas", "Cisternas na zona rural", Area.SANEAMENTO, 2e6, 4, 8000.0, "water", "Água da chuva guardada em casa"),
        ObraType("encostas", "Contenção de encostas", Area.SANEAMENTO, 10e6, 10, 15000.0, "rain", "Morro seguro na época de chuva"),
        ObraType("skatepark", "Skatepark", Area.CULTURA, 1e6, 3, 8000.0, "sport", "Pista para skate e patins"),
        ObraType("arena", "Arena de esportes", Area.CULTURA, 25e6, 18, 120000.0, "sport", "Jogos, lutas e shows", extra = mapOf(Area.TURISMO to 1.0), upkeep = 0.1),
        ObraType("cinema_rua", "Cinema de rua", Area.CULTURA, 4e6, 8, 30000.0, "theater", "Sessões baratas no centro"),
        ObraType("casa_shows", "Casa de shows", Area.CULTURA, 6e6, 10, 40000.0, "stage", "Música ao vivo e festas", extra = mapOf(Area.TURISMO to 1.0), nimby = 2.0),
        ObraType("horta", "Horta comunitária", Area.AMBIENTE, 500000.0, 3, 5000.0, "plant", "Verdura fresca cultivada pelos vizinhos", extra = mapOf(Area.SAUDE to 1.0)),
        ObraType("arborizacao", "Arborização de ruas", Area.AMBIENTE, 2e6, 6, 30000.0, "tree", "Sombra e menos calor"),
        ObraType("usina_solar", "Usina solar da prefeitura", Area.AMBIENTE, 8e6, 8, 100000.0, "bolt", "Energia limpa e conta de luz menor"),
        ObraType("artesanato", "Mercado de artesanato", Area.TURISMO, 3e6, 8, 30000.0, "store", "Renda para artesãos e vitrine para turistas", extra = mapOf(Area.ECONOMIA to 1.0)),
        ObraType("rota_gastronomica", "Rota gastronômica", Area.TURISMO, 1.5e6, 4, 40000.0, "party", "Bares e restaurantes no roteiro"),
        ObraType("centro_historico", "Restauração do centro histórico", Area.TURISMO, 15e6, 18, 80000.0, "theater", "Casarões e praças recuperados", extra = mapOf(Area.CULTURA to 1.0)),
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
        ObraType("hemocentro", "Hemocentro", Area.SAUDE, 25e6, 14, 500_000.0, "health", "Sangue para todos os hospitais do estado", Level.ESTADO),
        ObraType("hosp_cancer", "Hospital do câncer", Area.SAUDE, 120e6, 30, 800_000.0, "hospital", "Quimioterapia perto de casa", Level.ESTADO),
        ObraType("reabilitacao", "Centro de reabilitação", Area.SAUDE, 30e6, 16, 300_000.0, "emergency", "Fisioterapia e próteses", Level.ESTADO),
        ObraType("escola_militar", "Escola militar estadual", Area.EDUCACAO, 20e6, 14, 15_000.0, "school", "Disciplina e ensino médio", Level.ESTADO, extra = mapOf(Area.SEGURANCA to 1.0)),
        ObraType("ead", "Polo de educação a distância", Area.EDUCACAO, 8e6, 6, 100_000.0, "tech", "Faculdade pela internet no interior", Level.ESTADO),
        ObraType("ciops", "Centro integrado de comando", Area.SEGURANCA, 40e6, 14, 2_000_000.0, "camera", "Polícia, bombeiros e câmeras numa sala só", Level.ESTADO),
        ObraType("delegacia_mulher", "Delegacia da mulher", Area.SEGURANCA, 5e6, 6, 150_000.0, "shield", "Atendimento 24 horas", Level.ESTADO),
        ObraType("socioeducativo", "Centro socioeducativo", Area.SEGURANCA, 30e6, 18, 500_000.0, "gavel", "Internação de menores infratores", Level.ESTADO, nimby = 4.0),
        ObraType("zpe", "Zona de processamento de exportação", Area.ECONOMIA, 80e6, 24, 800_000.0, "factory", "Indústria voltada para fora", Level.ESTADO),
        ObraType("polo_tec", "Polo de tecnologia", Area.ECONOMIA, 40e6, 18, 400_000.0, "tech", "Empresas de software e pesquisa", Level.ESTADO, extra = mapOf(Area.EDUCACAO to 1.0)),
        ObraType("logistica", "Centro de distribuição logística", Area.ECONOMIA, 35e6, 14, 500_000.0, "store", "Galpões e caminhões para todo o Nordeste", Level.ESTADO, extra = mapOf(Area.TRANSPORTE to 1.0)),
        ObraType("rodoanel", "Rodoanel", Area.TRANSPORTE, 600e6, 40, 2_000_000.0, "road", "Caminhões longe da capital", Level.ESTADO),
        ObraType("trem_inter", "Trem intermunicipal", Area.TRANSPORTE, 500e6, 40, 1_000_000.0, "bus", "Trilhos entre a capital e o interior", Level.ESTADO),
        ObraType("hidrovia", "Balsa e hidrovia", Area.TRANSPORTE, 60e6, 18, 300_000.0, "water", "Travessia de rio sem esperar", Level.ESTADO),
        ObraType("dessal", "Dessalinizadora", Area.SANEAMENTO, 200e6, 30, 700_000.0, "water", "Água do mar para beber", Level.ESTADO),
        ObraType("barragem", "Barragem", Area.SANEAMENTO, 150e6, 30, 500_000.0, "water", "Reserva de água para a seca", Level.ESTADO, extra = mapOf(Area.ECONOMIA to 1.0)),
        ObraType("usina_lixo", "Usina de lixo e energia", Area.SANEAMENTO, 90e6, 24, 1_500_000.0, "recycle", "Lixo queimado vira luz", Level.ESTADO, extra = mapOf(Area.AMBIENTE to 1.0), nimby = 3.0),
        ObraType("estadio", "Estádio", Area.CULTURA, 400e6, 36, 2_000_000.0, "sport", "Futebol e grandes shows", Level.ESTADO, extra = mapOf(Area.TURISMO to 2.0), upkeep = 0.12),
        ObraType("orquestra", "Orquestra estadual", Area.CULTURA, 10e6, 6, 1_000_000.0, "theater", "Concertos nas cidades do interior", Level.ESTADO),
        ObraType("vila_olimpica", "Vila olímpica", Area.CULTURA, 30e6, 16, 300_000.0, "sport", "Esporte para crianças e atletas", Level.ESTADO, extra = mapOf(Area.SAUDE to 1.0)),
        ObraType("viveiro", "Viveiro de mudas", Area.AMBIENTE, 5e6, 6, 500_000.0, "plant", "Reflorestamento de áreas degradadas", Level.ESTADO),
        ObraType("nascentes", "Recuperação de nascentes", Area.AMBIENTE, 15e6, 12, 600_000.0, "water", "Rio com água o ano inteiro", Level.ESTADO, extra = mapOf(Area.SANEAMENTO to 1.0)),
        ObraType("brigada", "Brigada contra incêndios", Area.AMBIENTE, 12e6, 8, 800_000.0, "emergency", "Fogo apagado antes de virar queimada", Level.ESTADO),
        ObraType("estrada_parque", "Estrada-parque", Area.TURISMO, 80e6, 20, 600_000.0, "road", "Estrada bonita até o destino turístico", Level.ESTADO, extra = mapOf(Area.TRANSPORTE to 1.0)),
        ObraType("parque_tematico", "Parque temático", Area.TURISMO, 150e6, 30, 1_500_000.0, "party", "Atração para turistas de todo o país", Level.ESTADO, extra = mapOf(Area.ECONOMIA to 1.0)),
        ObraType("porto_cruzeiros", "Porto de cruzeiros", Area.TURISMO, 120e6, 30, 1_200_000.0, "beach", "Navios de turistas na capital", Level.ESTADO),
    )

    /** Obras federais: bilhões, anos de prazo, atendem milhões em um estado. */
    val OBRAS_PAIS = listOf(
        ObraType("hosp_univ", "Hospital universitário federal", Area.SAUDE, 900e6, 30, 3e6, "hospital", "Alta complexidade e formação de médicos", Level.PAIS),
        ObraType("policlinicas", "Rede de policlínicas do SUS", Area.SAUDE, 2.5e9, 18, 8e6, "emergency", "Especialistas e exames em várias cidades", Level.PAIS),
        ObraType("universidade", "Universidade federal", Area.EDUCACAO, 2e9, 36, 6e6, "tech", "Novo campus com cursos e pesquisa", Level.PAIS),
        ObraType("inst_federal", "Institutos federais", Area.EDUCACAO, 900e6, 18, 4e6, "school", "Ensino técnico em vários municípios", Level.PAIS),
        ObraType("pf", "Superintendência da Polícia Federal", Area.SEGURANCA, 400e6, 12, 6e6, "siren", "Combate ao crime organizado e às fronteiras", Level.PAIS),
        ObraType("presidio_federal", "Presídio federal", Area.SEGURANCA, 600e6, 24, 10e6, "gavel", "Isola os chefes das facções", Level.PAIS),
        ObraType("energia", "Parque eólico e solar", Area.ECONOMIA, 6e9, 30, 12e6, "bolt", "Energia barata para a indústria", Level.PAIS),
        ObraType("porto", "Porto", Area.ECONOMIA, 5e9, 36, 12e6, "factory", "Exportação e empregos", Level.PAIS),
        ObraType("br", "Duplicação de rodovia federal", Area.TRANSPORTE, 4e9, 30, 10e6, "road", "BR duplicada entre as capitais", Level.PAIS),
        ObraType("ferrovia", "Ferrovia", Area.TRANSPORTE, 15e9, 60, 20e6, "bus", "Carga e passageiros sobre trilhos", Level.PAIS),
        ObraType("canal", "Canal e adutoras", Area.SANEAMENTO, 9e9, 48, 12e6, "water", "Água do rio para o sertão", Level.PAIS),
        ObraType("moradia", "Conjuntos habitacionais", Area.SANEAMENTO, 5e9, 24, 5e6, "plant", "Casas com água e esgoto", Level.PAIS),
        ObraType("museu", "Museu nacional", Area.CULTURA, 500e6, 24, 10e6, "theater", "Acervo, teatro e biblioteca", Level.PAIS),
        ObraType("parque_nacional", "Parque nacional", Area.AMBIENTE, 300e6, 12, 10e6, "tree", "Floresta protegida e fiscalizada", Level.PAIS),
        ObraType("aeroporto_int", "Aeroporto internacional", Area.TURISMO, 4e9, 36, 20e6, "stage", "Voos diretos do exterior", Level.PAIS),
        ObraType("vacinas", "Fábrica de vacinas", Area.SAUDE, 3e9, 36, 15e6, "virus", "Vacina feita no Brasil", Level.PAIS, extra = mapOf(Area.ECONOMIA to 1.0)),
        ObraType("uti_aerea", "UTIs aéreas na Amazônia", Area.SAUDE, 800e6, 12, 5e6, "emergency", "Avião-hospital para quem mora longe", Level.PAIS),
        ObraType("hosp_escola", "Hospital-escola", Area.EDUCACAO, 1.2e9, 30, 4e6, "hospital", "Forma médicos e atende a população", Level.PAIS, extra = mapOf(Area.SAUDE to 1.0)),
        ObraType("pesquisa", "Centro de pesquisa espacial e de energia", Area.EDUCACAO, 5e9, 48, 10e6, "tech", "Ciência de ponta e patentes", Level.PAIS, extra = mapOf(Area.ECONOMIA to 2.0)),
        ObraType("fronteira", "Base de fronteira contra o tráfico", Area.SEGURANCA, 1.5e9, 18, 15e6, "shield", "Exército e PF nas fronteiras", Level.PAIS),
        ObraType("prf", "Polícia Rodoviária ampliada", Area.SEGURANCA, 700e6, 12, 10e6, "siren", "Mais postos nas BRs", Level.PAIS, extra = mapOf(Area.TRANSPORTE to 1.0)),
        ObraType("refinaria", "Refinaria", Area.ECONOMIA, 20e9, 48, 20e6, "factory", "Combustível refinado aqui", Level.PAIS, extra = mapOf(Area.ECONOMIA to 3.0, Area.AMBIENTE to -2.0)),
        ObraType("polo_naval", "Polo naval", Area.ECONOMIA, 8e9, 36, 12e6, "factory", "Navios e plataformas de petróleo", Level.PAIS),
        ObraType("zona_franca", "Zona Franca nova", Area.ECONOMIA, 3e9, 24, 8e6, "store", "Imposto menor para atrair indústria", Level.PAIS),
        ObraType("trem_rapido", "Trem de alta velocidade", Area.TRANSPORTE, 60e9, 72, 30e6, "bus", "Rio–São Paulo em 90 minutos", Level.PAIS, extra = mapOf(Area.TURISMO to 1.0)),
        ObraType("ponte", "Ponte entre estados", Area.TRANSPORTE, 4e9, 36, 8e6, "road", "Fim da balsa no rio", Level.PAIS),
        ObraType("aero_regionais", "Aeroportos regionais", Area.TRANSPORTE, 3e9, 30, 12e6, "stage", "Voos para cidades médias", Level.PAIS, extra = mapOf(Area.TURISMO to 1.0)),
        ObraType("cisternas_nac", "Programa nacional de cisternas", Area.SANEAMENTO, 2e9, 18, 10e6, "water", "Um milhão de cisternas no Semiárido", Level.PAIS),
        ObraType("despoluicao", "Despoluição de rio", Area.SANEAMENTO, 10e9, 48, 15e6, "plant", "Rio limpo de novo", Level.PAIS, extra = mapOf(Area.AMBIENTE to 2.0)),
        ObraType("alto_rendimento", "Centro de alto rendimento esportivo", Area.CULTURA, 1e9, 24, 10e6, "sport", "Atletas olímpicos treinando aqui", Level.PAIS),
        ObraType("cinemateca", "Cinemateca nacional", Area.CULTURA, 400e6, 18, 15e6, "theater", "Acervo e fomento ao cinema", Level.PAIS),
        ObraType("megaevento", "Sede de Copa ou Olimpíada", Area.CULTURA, 40e9, 60, 40e6, "trophy", "Os olhos do mundo no Brasil", Level.PAIS, extra = mapOf(Area.TURISMO to 5.0, Area.TRANSPORTE to 1.0), upkeep = 0.12),
        ObraType("satelite", "Satélite contra o desmatamento", Area.AMBIENTE, 1.5e9, 24, 20e6, "camera", "Alerta de desmatamento em tempo real", Level.PAIS),
        ObraType("corredor", "Corredor ecológico", Area.AMBIENTE, 800e6, 24, 12e6, "tree", "Floresta ligada de um parque ao outro", Level.PAIS),
        ObraType("fundo_amazonia", "Fundo Amazônia", Area.AMBIENTE, 2e9, 12, 15e6, "tree", "Dinheiro para quem preserva", Level.PAIS, extra = mapOf(Area.ECONOMIA to 1.0)),
        ObraType("rota_nacional", "Rota turística nacional", Area.TURISMO, 1.5e9, 18, 15e6, "beach", "Roteiro integrado pelos estados", Level.PAIS),
        ObraType("visite", "Campanha Visite o Brasil", Area.TURISMO, 600e6, 6, 20e6, "party", "Propaganda do país no exterior", Level.PAIS),
        ObraType("unesco", "Restauração de patrimônio da UNESCO", Area.TURISMO, 1e9, 24, 10e6, "theater", "Cidades históricas recuperadas", Level.PAIS, extra = mapOf(Area.CULTURA to 1.0)),
    )

    fun obrasFor(level: Level) = when (level) { Level.CIDADE -> OBRAS; Level.ESTADO -> OBRAS_ESTADO; Level.PAIS -> OBRAS_PAIS }

    private val ALL_OBRAS by lazy { (OBRAS + OBRAS_ESTADO + OBRAS_PAIS).associateBy { it.id } }
    fun obra(id: String) = ALL_OBRAS.getValue(id)

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
