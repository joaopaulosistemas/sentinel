# Checklist de coerência dos requisitos: Prova externa

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 008 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [x] CHK001 O relatório de prova lista todas as seções obrigatórias e o que torna o relatório inválido se faltar uma? [Completeness, Spec §FR-001, §FR-012]
- [x] CHK002 Está definido o procedimento para quem não participou do projeto repetir a medição, inclusive pré-requisitos? [Completeness, Spec §FR-002]
- [x] CHK003 Há requisito para o formato do mapa regra→categoria e para o momento em que ele é fixado? [Completeness, Spec §FR-007] — corrigido em 2026-11-15
- [x] CHK004 Está definido o histórico de correção de números já publicados? [Completeness, Spec §FR-012]

## Clareza e mensurabilidade

- [x] CHK005 "Dentro da tolerância declarada para o tempo" tem valor numérico? [Ambiguity, Spec §SC-001] — corrigido em 2026-11-15
- [x] CHK006 "Divergência relevante" tem critério objetivo para exigir correção? [Ambiguity, Spec §FR-006] — corrigido em 2026-11-15
- [x] CHK007 "Aceite real do formato padrão" tem definição verificável (serviço, URL, alertas exportados)? [Measurability, Spec §FR-010, §SC-006]
- [x] CHK008 "Publicável" é definido por campo de dado do repositório, e não por julgamento caso a caso? [Clarity, Spec §FR-011]

## Consistência

- [x] CHK009 O critério de 100 achados em 10 repositórios e 60 julgados por pessoa de fora é igual ao da 003 (SC-008)? [Consistency, Spec §SC-002]
- [x] CHK010 A regra de uma única medição final do reservado por versão é compatível com os registros de consulta da 003? [Consistency, Spec §FR-008, §SC-004]
- [x] CHK011 O campo `publicavel` do `repositorios.json` está definido na 003 e usado na 008 com o mesmo nome e regra? [Consistency, Spec §FR-011]
- [x] CHK012 O limite de concordância de 80 % e o tratamento abaixo dele são idênticos aos da 003? [Consistency, Spec §SC-002]

## Cenários e não funcionais

- [x] CHK013 Há requisito para quando o revisor externo não responde ou julga só parte da amostra? [Exception Flow, Gap] — corrigido em 2026-11-15
- [x] CHK014 Está definido o tratamento de ferramenta de mercado com licença que impede publicar resultados ou o conjunto de regras? [Dependency, Gap]
- [x] CHK015 O resultado do reservado pior que o de treino e validação é publicado com explicação, e isso é requisito? [Coverage, Spec §FR-009]
- [x] CHK016 As ações humanas (T015, T021, T030) estão ligadas a requisitos e a critérios de bloqueio? [Dependency, Traceability]
- [x] CHK017 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Revisão de 2026-11-15 feita pelo Claude a pedido do João: `[x]` = critério atendido no texto da spec, do plano ou dos contratos; `[ ]` = lacuna descrita no próprio item. Todas as lacunas foram corrigidas na spec em 2026-11-15 (itens marcados "corrigido").
- `/speckit-implement` lê o estado dos itens, mas não os altera.
