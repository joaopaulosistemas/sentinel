# Checklist de coerência dos requisitos: Menos alarme falso e só o novo no PR

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 004 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [x] CHK001 O que é "achado piorado" está definido para severidade e para excedente de ocorrências, com exemplos? [Completeness, Spec §FR-001]
- [x] CHK002 Há requisito para PR cujo base mudou depois da auditoria de referência? [Completeness, Spec §FR-003]
- [x] CHK003 Está definido o comportamento para arquivo renomeado e alterado no mesmo PR? [Edge Case, Spec §FR-002]
- [x] CHK004 O agrupamento por causa define a chave de agrupamento de forma verificável? [Completeness, Spec §FR-007] — corrigido em 2026-11-15

## Clareza e mensurabilidade

- [x] CHK005 "Estilo e qualidade" versus "segurança" em código gerado tem lista fechada de categorias? [Clarity, Spec §FR-005]
- [x] CHK006 O limite de baixa precisão para rebaixar uma regra está numérico (amostra mínima e corte) no requisito? [Ambiguity, Spec §FR-006] — corrigido em 2026-11-15
- [x] CHK007 A meta de redução de achados no SC-005 depende de um número que o João fixa depois da linha de base; há prazo ou gatilho para isso? [Measurability, Spec §SC-005] — corrigido em 2026-11-15
- [x] CHK008 A linha de contagem de achados antigos tem texto e posição definidos? [Clarity, Spec §FR-012]

## Consistência

- [x] CHK009 O corte 80/50 de confiança (FR-008) é coerente com o mínimo de amostra da 003 e com a faixa publicada na 008? [Consistency, Spec §FR-008]
- [x] CHK010 O veredito exigido (FR-009) usa o mesmo vocabulário "fica/sai/inconclusivo" da 003? [Consistency, Spec §FR-009]
- [x] CHK011 As chaves de configuração por sistema (princípio V) são as mesmas citadas no plano e nas tarefas? [Consistency, Conflict]
- [x] CHK012 O uso do mesmo corte para o SARIF e para o comentário está dito nos requisitos, e não só nas tarefas? [Consistency, Gap]

## Cenários e não funcionais

- [x] CHK013 Há requisito para o que o PR mostra quando a auditoria de referência não existe? [Exception Flow, Spec §FR-003]
- [x] CHK014 Está definido que a chave desligada mantém o comportamento atual byte a byte? [Coverage, Spec §FR-011]
- [x] CHK015 Existe requisito de tempo adicional do cálculo de "novo" em PRs grandes? [Non-Functional, Gap] — corrigido em 2026-11-15
- [x] CHK016 A mudança de campo na API (`ruleBand`, `groupBy`) tem requisito de compatibilidade para clientes antigos? [Dependency, Gap] — corrigido em 2026-11-15
- [x] CHK017 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Revisão de 2026-11-15 feita pelo Claude a pedido do João: `[x]` = critério atendido no texto da spec, do plano ou dos contratos; `[ ]` = lacuna descrita no próprio item. Todas as lacunas foram corrigidas na spec em 2026-11-15 (itens marcados "corrigido").
- `/speckit-implement` lê o estado dos itens, mas não os altera.
