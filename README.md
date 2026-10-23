# Palanque

Simulador político para Android: comece numa cidade real do Brasil e suba, mandato a mandato, da Câmara Municipal ao Palácio do Planalto. Orçamento com os pisos de saúde e educação e a Lei de Responsabilidade Fiscal, obras no mapa, secretários, crises, campanha e urna — em tempo real com pausa.

**Versão 0.5:** carreira de vereador a governador. Comece como vereador, prefeito, deputado estadual, deputado federal ou governador — em qualquer cidade ou estado — e escolha o próximo degrau a cada eleição.

## Baixar

A cada envio para `main`, o GitHub Actions roda os testes, gera o APK e publica em **Releases** (`Palanque.apk`). A assinatura é fixa, então cada versão instala por cima da anterior. O próprio jogo procura e instala atualizações.

## Como jogar

- **Mapa:** o contorno real da cidade, dividido em bairros. Toque num bairro para ver aprovação, moradores e obras.
- **Governo:** gasto por área, IPTU e ISS, empréstimos. Fure o piso de saúde (15%), de educação (25%) ou o teto de pessoal (54%) e o Tribunal de Contas rejeita as contas; duas rejeições deixam você inelegível.
- **Obras:** 24 tipos, cada um com custo, prazo e quantas pessoas atende. Inaugurar dá aprovação no bairro.
- **Equipe:** um secretário por área — competência, honestidade e lealdade.
- **Câmara:** vereadores com partido, reduto e relação com você. Envie leis prontas ou criadas por você, negocie (conversa, emenda, cargo, obra prometida) e cuide da maioria: oposição forte abre CPI e, com o governo impopular, vota a cassação.
- **Menu de pausa:** liga e desliga os eventos aleatórios a qualquer momento.
- **Governador:** o mapa do estado com os municípios reais, ICMS e IPVA, FPE, piso de saúde de 12% e teto de pessoal de 49%, obras estaduais (hospital regional, batalhão da PM, rodovia, adutora, metrô…), Assembleia Legislativa e prefeitos aliados ou rivais em cada município.
- **Vereador e deputados:** vote os projetos do governo (orientação do partido e opinião do eleitor), proponha leis, mande emendas para o seu reduto e escolha entre base, independente e oposição. Discurso na tribuna e CPI rendem capital político. A eleição é proporcional, pelo quociente.
- **Carreira:** em 20 de julho de cada ano de eleição você escolhe o que disputar. Municipais (2032, 2036…): vereador ou prefeito. Gerais (2030, 2034…): deputado ou governador. Mudar de nível exige deixar o cargo; perdendo, você espera a próxima eleição.
- **Campanha:** convenção em 20 de julho (vice e coligação, que dão verba e tempo de TV), campanha nas ruas a partir de 16 de agosto com comícios e propaganda, dois debates na TV, ataques dos adversários, pesquisas semanais com mapa de intenção de voto por bairro e segundo turno nas cidades com mais de 200 mil eleitores. Promessas feitas nos debates são cobradas dois anos depois.

## Como o projeto está organizado

- `app/src/main/java/.../sim` — simulação em Kotlin puro (orçamento, áreas, obras, eventos, eleições), sem dependência da interface.
- `app/src/main/java/.../world` — leitura do mapa e geração dos bairros (Voronoi recortado pelo contorno real).
- `app/src/main/java/.../ui` — telas em Jetpack Compose; o mapa é desenhado em Canvas.
- `app/src/main/assets/brasil.bin` — 27 estados e 5.570 municípios com contorno e dados, gerado por `tools/build_brasil.py` a partir da base do [Retrato do Brasil](https://dmwnezes.com/estatisticas).
- `app/src/test` — testes da simulação (8 anos de jogo em quatro cidades, saves) e capturas das telas.

## Créditos

- Malhas e dados: IBGE (Censo 2022, estimativas, PIB dos Municípios), PNUD/Ipea (IDH), Ipea/FBSP (Atlas da Violência), TSE.
- Fonte: Outfit (OFL).
- Os bairros e os políticos do jogo são fictícios; os partidos são os registrados no TSE.

criado por: @dmwnezes
