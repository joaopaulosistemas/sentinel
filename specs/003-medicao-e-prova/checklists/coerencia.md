# Checklist de coerência dos requisitos: Medição e prova (a régua)

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 003 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [ ] CHK001 Estão definidos todos os campos mínimos do relatório por pilha e categoria, inclusive tempo e tamanho do repositório? [Completeness, Spec §FR-001, §FR-018]
- [ ] CHK002 Os requisitos dizem o que acontece quando uma medida está incompleta (repositório sem `completa`) em vez de virar zero? [Completeness, Spec §FR-003]
- [ ] CHK003 Está especificado como o histórico de correção de gabarito guarda o valor anterior e o motivo? [Completeness, Spec §FR-015]
- [ ] CHK004 Há requisito para o formato da proposta de mudança e para o que fazer quando o critério é registrado depois da medição? [Completeness, Spec §FR-009]

## Clareza e mensurabilidade

- [ ] CHK005 O "mínimo das premissas" para amostra pequena está citado com o valor (20) no próprio requisito, e não só nas premissas? [Clarity, Spec §FR-002]
- [ ] CHK006 A "tolerância" de piora por categoria tem valor ou regra de cálculo definidos, e não só a referência às travas atuais? [Ambiguity, Spec §FR-010]
- [ ] CHK007 "Mesmas entradas" na repetibilidade está definido (commit, versão de ferramenta, semente) de modo verificável? [Clarity, Spec §FR-004]
- [ ] CHK008 O critério de "inconclusivo" é distinguível de "sai" por regra objetiva? [Measurability, Spec §FR-010]

## Consistência

- [ ] CHK009 O requisito da separação treino/validação/reservado é consistente com as travas atuais de separação que FR-012 manda manter? [Consistency, Spec §FR-005, §FR-012]
- [ ] CHK010 O mínimo de 100 achados em 10 repositórios do SC-008 coincide com o critério do placar citado e com o da spec 008 (SC-002)? [Consistency, Spec §SC-008]
- [ ] CHK011 O limite de concordância (0,80) aparece igual aqui, na 008 e nos contratos? [Consistency, Spec §FR-014]
- [ ] CHK012 Os nomes dos subcomandos nos requisitos e no contrato da CLI são os mesmos usados por 004, 007 e 008? [Consistency, Conflict]

## Cobertura de cenários

- [ ] CHK013 Está definido o que ocorre quando o conjunto reservado precisa ser trocado ou esgotado (promoção sem substituto)? [Coverage, Edge Case, Spec §FR-007]
- [ ] CHK014 Há requisito para repositório de medição que sai do ar ou muda de commit? [Edge Case, Gap]
- [ ] CHK015 Estão definidos limites de tempo e de memória da medição para repositórios muito grandes? [Coverage, Gap]
- [ ] CHK016 O que acontece com medições antigas quando a versão da ferramenta muda? [Recovery, Gap]

## Não funcionais, dependências e premissas

- [ ] CHK017 A proibição de executar código auditado (FR-017) cobre também o uso de repositórios como fixtures de teste? [Coverage, Spec §FR-017]
- [ ] CHK018 A dependência de tempo humano para a revisão de precisão está declarada com o que bloqueia se faltar? [Dependency, Spec §SC-008]
- [ ] CHK019 O bloqueio de GitHub Actions por cobrança está registrado como premissa que afeta as verificações de CI? [Assumption, Gap]
- [ ] CHK020 Está claro quais itens exigem autorização do João (T004, T050, T053, T057, T060, T069) e quais já foram autorizados? [Dependency, Traceability]
- [ ] CHK021 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Itens ficam desmarcados até a revisão do João; este comando não marca nenhum item.
- `/speckit-implement` lê o estado dos itens, mas não os altera.
