# Palanque

Simulador político para Android: comece numa cidade real do Brasil e suba, mandato a mandato, da Câmara Municipal ao Palácio do Planalto. Orçamento com os pisos de saúde e educação e a Lei de Responsabilidade Fiscal, obras no mapa, secretários, crises, campanha e urna — em tempo real com pausa.

**Versão 0.2:** prefeito jogável em qualquer um dos 5.570 municípios, com Câmara Municipal, leis e negociação de votos.

## Baixar

A cada envio para `main`, o GitHub Actions roda os testes, gera o APK e publica em **Releases** (`Palanque.apk`). A assinatura é fixa, então cada versão instala por cima da anterior. O próprio jogo procura e instala atualizações.

## Como jogar (prefeito)

- **Mapa:** o contorno real da cidade, dividido em bairros. Toque num bairro para ver aprovação, moradores e obras.
- **Governo:** gasto por área, IPTU e ISS, empréstimos. Fure o piso de saúde (15%), de educação (25%) ou o teto de pessoal (54%) e o Tribunal de Contas rejeita as contas; duas rejeições deixam você inelegível.
- **Obras:** 24 tipos, cada um com custo, prazo e quantas pessoas atende. Inaugurar dá aprovação no bairro.
- **Equipe:** um secretário por área — competência, honestidade e lealdade.
- **Câmara:** vereadores com partido, reduto e relação com você. Envie leis prontas ou criadas por você, negocie (conversa, emenda, cargo, obra prometida) e cuide da maioria: oposição forte abre CPI e, com o governo impopular, vota a cassação.
- **Menu de pausa:** liga e desliga os eventos aleatórios a qualquer momento.
- **Carreira:** em agosto de 2032 começa a campanha: pesquisas semanais, comícios, propaganda e segundo turno nas cidades com mais de 200 mil eleitores.

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
