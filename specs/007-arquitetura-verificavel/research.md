# Pesquisa: Sentinel Premium 5 — Arquitetura verificável

Levantado no repositório em 2026-11-13.

## O que já existe

| Peça | Onde | Estado |
|---|---|---|
| Política declarativa de arquitetura | `audit-rules/packs/core-architecture/architecture.yml`, `pack/ArchitecturePolicy.java` (`layers`, `forbid`, `allow`, `strict`, `conventions`, `required-files`, `components`) | existe; camadas por **glob de caminho/pacote**; regra `from → to` só sobre **import direto** em texto |
| Aplicação da política | `analyzer/ArchitecturePolicyAnalyzer.java` | existe; só lê arquivos (VII) |
| Analisador de arquitetura em Java | `analyzer/ArchitectureAnalyzer.java` (camadas, entidade exposta em controlador) | existe |
| Ciclos de dependência | `analyzer/DependencyCyclesAnalyzer.java`, `analyzer/structure/DependencyCycles.java` | existe: ciclos de **pacotes** Java e de **arquivos** TS/JS por texto de import; não há ciclo entre **módulos** |
| Peso da dimensão | `service/ScoringService.java`: `Category.ARCHITECTURE, 0.20` | existe |
| Modo sombra | `shadow: true` nos perfis (aparece, não pesa) | existe; serve para o verificador novo conviver |
| Diferença entre auditorias (novos, resolvidos, persistentes) | rota de diferença; `FindingLifecycleService`, `Fingerprints` | existe; reaproveitável para a tendência |
| Mapa recuperado, dependência indireta, exceção com validade, medida de acoplamento, violação plantada | — | **não existe** |
| Modelo estrutural | spec 005 (só documentos) | ainda não existe |

Observação: o analisador atual decide a camada por **caminho** (glob). Isso é a camada **declarada**. A camada **inferida** do mapa recuperado vem de anotação e estereótipo (esclarecimento 1) e não substitui a declarada.

## D1. Verificador novo ao lado do atual, em modo sombra

**Decisão:** um analisador novo (`ArquiteturaVerificavelAnalyzer`) lê o modelo da 005 e produz o mapa e os desvios. `ArchitecturePolicyAnalyzer` e `ArchitectureAnalyzer` ficam intactos (FR-006). Os achados novos nascem em modo sombra (aparecem, não pesam), de modo que o peso de 20 % não muda (FR-011). Só o veredito "fica" da 003 tira do sombra e, aí, substitui a verificação equivalente.
**Alternativa:** estender `ArchitecturePolicyAnalyzer` no lugar: arrisca regressão nas verificações de hoje (SC-004) e mistura medida de dois verificadores.

## D2. Formato estendido de `architecture.yml`, compatível

**Decisão:** o formato atual continua válido sem alteração (História 2, cenário 3). Chaves opcionais novas: `indirect: true` em uma regra `forbid` (segue o caminho por classes intermediárias), `cycles: forbid` (ciclos entre módulos proibidos) e `exceptions` (`from`, `to`, `reason`, `registered`, `expires`; validade obrigatória, esclarecimento 2). Contrato em `contracts/architecture-yml-estendido.md`.
**Alternativa:** novo arquivo `architecture-v2.yml`: quebra o plug-and-play (V) e duplica o que já existe.

## D3. Mapa recuperado

**Decisão:** módulos = módulos de build (`pom.xml`, Gradle, workspaces de `package.json`) e, sem eles, pacotes de primeiro nível; camadas = anotações e estereótipos do modelo, com "sem camada" para o resto (esclarecimento 1); dependência = relações do modelo entre unidades de módulos diferentes, cada uma com ao menos uma evidência (arquivo e linha, FR-001); relação não resolvida do modelo vira dependência "não resolvida" (FR-004). Testes e código gerado ficam fora, contados (reuso de `GeneratedFiles`).
**Alternativa:** inferir camada por nome de pacote: descartada no esclarecimento 1 (pouco objetiva).

## D4. Desvio direto e indireto

**Decisão:** para cada regra `forbid from→to`, busca de caminho mais curto no grafo de classes, de um membro de `from` a um membro de `to`; o desvio mostra o caminho completo, com evidência por aresta (FR-003). Sem `indirect`, só o caminho de uma aresta (compatível com hoje). Limite de comprimento configurável.
**Alternativa:** fecho transitivo completo por módulo: mais barato, mas some a classe culpada.

## D5. Medida do verificador com violações plantadas

**Decisão:** corpus em `audit-rules/calibracao/arquitetura/` com repositórios pequenos que **respeitam** a arquitetura (treino ou criados para o teste; reservado não é lido, III) e **patches** que plantam violações conhecidas (diretas, indiretas, ciclo) com gabarito em JSON. A medição aplica o patch em **cópia temporária**, nunca no original, e calcula acerto e alarme falso com Wilson 95 % (mesma estatística da 003). Sem a régua da 003 (hoje só documentos), a medição é um script local e o "fica" fica bloqueado.
**Alternativa:** plantar violação por mutação automática: reproduzível, mas não representa erro humano; fica como evolução.

## D6. Exceções com validade

**Decisão:** `expires` obrigatório; a conferência usa a **data da auditoria**, não o relógio da máquina de quem lê o relatório. Vencida, a dependência volta a ser desvio e a exceção sai como vencida no relatório.

## D7. Erosão e acoplamento

**Decisão:** cada violação vira um achado cujo identificador é regra + par origem→destino, sem a linha (esclarecimento 4); a rota de diferença existente já entrega novas, resolvidas e persistentes. Métricas por módulo: instabilidade de Martin (saem ÷ saem + entram) e contagem bruta (esclarecimento 3), mais número de ciclos; guardadas como **resumo** com o resultado da auditoria (sem tabela nova), para a tendência comparar duas auditorias.
**Pendência:** onde o resumo mora no resultado (campo do resultado ou arquivo anexo) e se isso muda a resposta da API: **autorização do João** se mudar.

## D8. PR (spec 004)

**Decisão:** violações e ciclos são achados e passam pelo classificador da 004 (`PrClassifier`); só novos e piorados entram no comentário (FR-008, SC-005). Depende de a 004 existir; sem ela, o comportamento atual por arquivo.

## D9. Ciclos e sinais clássicos (P3)

**Decisão:** reaproveitar `DependencyCycles` e acrescentar o ciclo **entre módulos** sobre o mapa; "classe que sabe demais" = fan-out acima de um limite declarável; módulo instável = instabilidade acima do limite. Limites em `architecture.yml` (`metrics:`), com padrões propostos, a calibrar.

## D10. Configuração por sistema

**Decisão:** `.sentinel.yml` do sistema: `architecture.verify.enabled` (padrão **desligado**), `architecture.max-path-length`. Nada em `application.properties` (princípio V).

## Pendências

1. Spec 005 (modelo) e 003 (régua) antes da medição.
2. **Autorização do João** se o mapa ou a tendência precisarem de campo novo na API pública.
3. Limites de métricas e comprimento de caminho são propostas a calibrar.
