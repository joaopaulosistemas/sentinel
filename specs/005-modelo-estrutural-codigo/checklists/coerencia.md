# Checklist de coerência dos requisitos: Modelo estrutural do código

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 005 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [ ] CHK001 A lista de unidades, relações e pontos de entrada do modelo está fechada, com os estados de relação definidos? [Completeness, Spec §FR-001, §FR-005]
- [ ] CHK002 Está especificado o que o modelo guarda sobre o módulo de build de cada arquivo, já que a 007 precisa dele? [Gap, Dependency]
- [ ] CHK003 Há requisito de fluxo de valores, ou está dito que ele fica para a 006? [Gap, Spec §FR-010]
- [ ] CHK004 Estão definidos os limites de tempo e memória e o formato do modelo parcial? [Completeness, Spec §FR-009]

## Clareza e mensurabilidade

- [ ] CHK005 "Orçamento definido pelo João" tem valor, ou há gatilho que obriga o João a fixá-lo antes de medir? [Ambiguity, Spec §FR-009, §SC-005]
- [ ] CHK006 "Não resolvido" tem lista de motivos válidos para os testes conferirem? [Clarity, Spec §FR-005]
- [ ] CHK007 Relações "parciais" têm regra clara para a parte fixa e a parte variável do caminho? [Clarity, Spec §FR-014]
- [ ] CHK008 O critério "100 % dos endpoints esperados e nenhum extra" tem gabarito definido por repositório de teste? [Measurability, Spec §SC-001]

## Consistência

- [ ] CHK009 A regra de que o modelo não é guardado entre auditorias (FR-015) é compatível com o reuso por 006 e 007 na mesma auditoria? [Consistency, Spec §FR-010, §FR-015]
- [ ] CHK010 A decisão do ponto de entrada local no lugar de rotas na API está refletida nos requisitos de consulta (FR-004)? [Consistency, Conflict]
- [ ] CHK011 A exceção ao princípio V para as regras do piloto está justificada nos requisitos e na tabela de complexidade? [Consistency, Assumption]
- [ ] CHK012 A dependência nova (JavaParser) e a regra de escopo de tecnologias da constituição são coerentes entre FR-011 e as tarefas? [Consistency, Spec §FR-011]

## Cenários e não funcionais

- [ ] CHK013 Há requisito para frontend sem Node disponível (modelo parcial com motivo)? [Exception Flow, Gap]
- [ ] CHK014 Está definido o tratamento de arquivo com erro de sintaxe sem derrubar a auditoria? [Edge Case, Spec §FR-006]
- [ ] CHK015 Código gerado e de terceiros têm tratamento definido no modelo? [Coverage, Spec §FR-006]
- [ ] CHK016 A segurança do leitor (nunca executar nem configurar `eslint` do repositório auditado) é requisito e não só tarefa? [Non-Functional, Gap]
- [ ] CHK017 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Itens ficam desmarcados até a revisão do João; este comando não marca nenhum item.
- `/speckit-implement` lê o estado dos itens, mas não os altera.
