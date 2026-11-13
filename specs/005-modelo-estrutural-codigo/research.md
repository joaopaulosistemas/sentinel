# Pesquisa: Sentinel Premium 3 — Modelo estrutural do código

Levantado no repositório em 2026-11-13.

## O que já existe

| Peça | Onde | Estado |
|---|---|---|
| Analisadores Java próprios que leem **texto** (imports, ciclos, camadas) | `analyzer/ArchitectureAnalyzer`, `DependencyCyclesAnalyzer`, `analyzer/structure/*` (`SourceFiles` lê os arquivos sem seguir links) | existem; não constroem árvore nem grafo de chamadas |
| Leitura sintática de Java | — | **não existe**: o `pom.xml` do `audit-api` (Quarkus) não tem biblioteca de parser |
| Node 24 na imagem | `Dockerfile` (`WITH_ESLINT`, padrão ligado) com ESLint e `typescript-eslint` | existe; `EslintAnalyzer` já roda `--no-config-lookup` para nunca executar configuração do repositório |
| Joern (taint Java entre arquivos) | `Dockerfile` `WITH_JOERN=false`, ~2 GB, `audit-api/joern/`, `SarifAnalyzer` | opcional, desligado por padrão |
| Rotas de dados e consulta por estrutura | — | não existe |
| CLI | `audit-cli` (cliente HTTP) | não carrega o motor; o comando de consulta entra no ponto de entrada local do `audit-api` (D5) |

Observação: a spec e o plano de 004 falam em "Spring Boot"; o **próprio Sentinel** usa Quarkus. O modelo precisa entender os **dois** em repositórios auditados (FR-002), não o framework do Sentinel.

## D1. Técnica para Java: leitor sintático em Java puro (biblioteca **nova**) — **decisão do João**

**Decisão proposta:** biblioteca de leitura sintática de Java (JavaParser) como dependência Maven do `audit-api`; lê só texto, sem compilar nem executar (princípio VII), roda dentro do JRE que a imagem já tem (princípio VIII).
**Motivo:** a única opção que entrega árvore completa de Java sem binário novo, sem Node e sem os 2 GB do Joern; resolução de nomes por importação (caso de borda da spec) é feita por nós em cima da árvore.
**Alternativas:** (a) estender os analisadores de texto com expressões regulares: não distingue chamada de comentário nem resolve nomes, é o que a spec quer superar; (b) Joern: pesado, opcional, é comparado e não substituído (premissa da spec); (c) tree-sitter: binários nativos por plataforma, mais frágil no Docker da máquina do trabalho; (d) compilar e usar o compilador: proibido (VII).
**Por que pede o João:** a premissa da spec diz para não introduzir tecnologia nova sem a data combinada; uma dependência Maven nova é isso. Sem o "sim", a fatia B não começa e a A (esqueleto e contrato) vale sozinha.

## D2. Técnica para TypeScript/JavaScript: parser que já vem na imagem

**Decisão:** um script nosso, versionado em `audit-api/…/modelo/`, executado pelo Node 24 da imagem, que só chama o parser do `typescript-eslint` (`parse`, nunca `eslint`, nunca carrega config do repositório) e imprime a árvore como JSON. Angular, React e Vue: decoradores, JSX e `<script>` de `.vue` (extraído como texto) são lidos pelo mesmo parser.
**Motivo:** reaproveita o que já está instalado e o padrão de segurança do `EslintAnalyzer`; sem dependência nova.
**Alternativa:** parser em Java para TypeScript: não há maduro em Java puro. Sem Node (imagem com `WITH_ESLINT=false`), o frontend vira "não modelado" com o motivo, declarado.

## D3. Forma do modelo

**Decisão:** um grafo em memória, reconstruído a cada auditoria (esclarecimento 5), com quatro tabelas lógicas: unidades, relações (resolvida, externa, não resolvida com motivo), pontos de entrada e acessos a dados. Serialização JSON determinística (chaves ordenadas, identificadores estáveis `arquivo#símbolo`). Só o resumo de cobertura e o tempo vão para o resultado da auditoria.
**Alternativa:** guardar no banco: descartada pelo esclarecimento 5.

## D4. Resolução de chamadas

**Decisão:** por nome qualificado usando imports, pacote e tipo declarado; chamada a biblioteca vira relação `externa` com nome qualificado (esclarecimento 3, FR-013); reflexão, injeção por nome e proxy viram `não resolvida` com motivo. Caminho de API do frontend montado por partes vira `parcial` (esclarecimento 4, FR-014). Nome igual em pacotes diferentes: distinguido pelo import.
**Limite:** nada de análise de fluxo de dados nesta spec (é a 006).

## D5. Consulta (esclarecimento 1)

**Decisão:** comando da CLI `modelo` com subcomandos `endpoints`, `chamadores`, `anotacao`, `dados`, `cobertura`; saída em texto e `--json`. As regras usam a mesma biblioteca por dentro. Sem rota na API, sem tela. **Ponto de entrada local (decisão de reconciliação):** o `audit-cli` é só um cliente HTTP da API (depende apenas de jackson) e não carrega os motores. Os comandos abaixo rodam num ponto de entrada local `com.sentinel.audit.local.LocalMain` dentro do `audit-api`, chamado no Docker pelo script `scripts/sentinel-local` (princípio VIII; nada no host). O `audit-cli` e a API pública **não mudam**. Alternativa descartada por ora: rotas novas na API + subcomandos no `audit-cli` (muda a API pública; só com o "sim" do João, princípio IX).

## D6. Regras do piloto e medição

**Decisão:** escolher as 3 regras em `docs/evolucao/precisao.md` e nos gabaritos de **treino** (princípio III), por falso positivo ou falso negativo causado por olhar só texto; a escolha é registrada antes de escrever as versões. Cada uma convive com a regra de padrão; o veredito vem da régua da spec 003.
**Estado real:** a régua da 003 foi revertida (só especificação). Até ela existir, a comparação é manual sobre treino (gabarito) e o "fica" fica **bloqueado**: o piloto entrega as versões e a medida, sem substituir nenhuma regra.

## D8. Como uma regra consulta o modelo — **decisão do João**

**Problema:** o motor atual lê regras declarativas (padrões em YAML); não há como um YAML perguntar "quem chama X" ao modelo. O princípio V pede regra nova sem compilar.
**Decisão proposta:** nas 3 regras piloto, analisadores Java no estilo de `ArchitectureAnalyzer` que consomem a interface pública do modelo e leem parâmetros (limites, nomes de anotações, ligar/desligar) do perfil YAML. Uma linguagem de consulta declarativa em YAML fica para depois do piloto, se o valor for provado.
**Alternativa:** construir já a linguagem declarativa: atende o princípio V por inteiro, mas é trabalho grande e especulativo antes de provar valor.
**Custo:** exceção ao princípio V para essas 3 regras (registrada em "Complexity Tracking" do plano).

## D7. Cobertura e orçamento

**Decisão:** cobertura por repositório e por tipo de construção (classe, método, rota, chamada, componente), com motivo do que ficou de fora; código gerado e bibliotecas embutidas excluídos por padrão e contados (reaproveita `GeneratedFiles`, `VendoredCode`). Tempo de construção medido e gravado; limite de tempo e memória por repositório, com relatório do que ficou de fora. O orçamento numérico é do João, depois da linha de base.

## Pendências

1. **Decisão do João:** dependência Maven nova para Java (D1).
2. O orçamento de tempo (SC-005) e a escolha final das 3 regras dependem da linha de base da 003.


## Decisões do João (2026-11-13)

Aprovadas as recomendadas: dependência JavaParser (D1); exceção ao princípio V em D8 (regras piloto como analisadores Java, com justificativa registrada no Complexity Tracking); campos opcionais na API das specs 006 e 007; mudanças em `ci.yml` das specs 006 e 008; ponto de entrada local no lugar de rotas novas. Licença do conjunto de regras da comunidade (008): conferir antes do primeiro uso e registrar no relatório; sem licença compatível, a comparação de mercado não o usa. Continua valendo: nada é implementado até o João liberar.
