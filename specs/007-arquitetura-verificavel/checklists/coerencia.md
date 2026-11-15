# Checklist de coerência dos requisitos: Arquitetura verificável

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 007 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [ ] CHK001 A lista de sinais de camada (anotações e estereótipos) está definida por pilha, com o tratamento de "sem camada"? [Completeness, Spec §FR-001]
- [ ] CHK002 Está especificado como o mapa trata monorepo com vários sistemas? [Completeness, Edge Case]
- [ ] CHK003 As métricas por módulo têm limites padrão e a forma de configurá-los por sistema? [Gap, Spec §FR-007]
- [ ] CHK004 O formato estendido de `architecture.yml` define `indirect`, `cycles` e `exceptions` com campos obrigatórios? [Completeness, Spec §FR-002, §FR-009]

## Clareza e mensurabilidade

- [ ] CHK005 "Dependência indireta" tem limite de comprimento do caminho definido? [Ambiguity, Spec §FR-003]
- [ ] CHK006 A data de validade de uma exceção é comparada com a data da auditoria, e isso está dito no requisito? [Clarity, Spec §FR-009]
- [ ] CHK007 A identidade da violação (regra + par origem→destino) cobre também renomeação e movimentação, com o efeito descrito? [Clarity, Spec §FR-007]
- [ ] CHK008 O "100 % dos módulos e dependências do gabarito" tem gabarito por repositório de teste? [Measurability, Spec §SC-001]

## Consistência

- [ ] CHK009 A leitura de módulos de build pela própria 007 é coerente com a 005, que não guarda o módulo? [Consistency, Dependency]
- [ ] CHK010 O verificador em sombra com peso 0,20 inalterado é coerente com FR-006 e FR-011? [Consistency, Spec §FR-006, §FR-011]
- [ ] CHK011 O corte em PR reutiliza o classificador da 004 de forma que o requisito FR-008 e o plano dizem a mesma coisa? [Consistency, Spec §FR-008]
- [ ] CHK012 O comando `arquitetura` usa o ponto de entrada local, sem rota nova, tanto nos requisitos quanto nos contratos? [Consistency]

## Cenários e não funcionais

- [ ] CHK013 Há requisito para código de teste e código gerado fora do mapa, com contagem exibida? [Coverage, Edge Case]
- [ ] CHK014 Está definido o que acontece com injeção de dependência por interface ou por nome? [Exception Flow, Spec §FR-004]
- [ ] CHK015 Violações plantadas em cópia temporária estão no requisito de segurança (nunca no repositório auditado)? [Non-Functional, Spec §FR-012]
- [ ] CHK016 O campo de métricas na API pública tem requisito de compatibilidade? [Dependency, Gap]
- [ ] CHK017 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Itens ficam desmarcados até a revisão do João; este comando não marca nenhum item.
- `/speckit-implement` lê o estado dos itens, mas não os altera.
