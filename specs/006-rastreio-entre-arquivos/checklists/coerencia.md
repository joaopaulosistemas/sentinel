# Checklist de coerência dos requisitos: Rastreio de dado inseguro entre arquivos

**Purpose**: Validar a qualidade (completude, clareza, consistência, mensurabilidade) dos requisitos da spec 006 antes de liberar a implementação
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

**Note**: Checklist gerado por `/speckit-checklist` (foco: coerência entre as specs 003 a 008; profundidade padrão; uso: revisão pelo João antes de implementar).
**Review Ownership**: Checklist de qualidade de requisitos, de propriedade do revisor. Marque `[x]` só quando o revisor concluir que o critério foi atendido.
**Marker Semantics**: `[x]` significa critério de qualidade do requisito revisado e satisfeito. Não significa que a implementação esteja pronta.

## Completude

- [ ] CHK001 Estão listadas as origens e os destinos de SQL cobertos, incluindo Quarkus Panache e JPQL por `EntityManager`? [Completeness, Spec §FR-002]
- [ ] CHK002 Está definido o formato do achado (origem, passos, destino) e como o caminho truncado é marcado? [Completeness, Spec §FR-004]
- [ ] CHK003 Há requisito de como a lista de propagadores e sanitizadores é versionada e validada? [Gap, Spec §FR-003, §FR-013]
- [ ] CHK004 Estão definidos os valores padrão de profundidade, caminhos e tempo? [Gap, Spec §FR-010]

## Clareza e mensurabilidade

- [ ] CHK005 "Sanitizador declarado" tem forma de declaração e de registro no achado eliminado? [Clarity, Spec §FR-005]
- [ ] CHK006 "Rastro perdido" tem lista de motivos (reflexão, assíncrono, limite) fechada? [Clarity, Spec §FR-006]
- [ ] CHK007 "Mesmo destino" para fusão usa arquivo e linha da execução e está livre de ambiguidade quando há várias linhas? [Ambiguity, Spec §FR-007]
- [ ] CHK008 O "acerto maior ou igual e alarme falso menor ou igual" do SC-002 define a comparação com Joern e com o padrão sobre o mesmo conjunto? [Measurability, Spec §SC-002]

## Consistência

- [ ] CHK009 A dependência do modelo da 005 sem fluxo de valores é coerente com o que a 005 promete (FR-010)? [Conflict, Spec §FR-001]
- [ ] CHK010 O comando `rastreio executar` usa o ponto de entrada local e o formato que `benchmark-owasp.py` aceita, sem contradição entre plano e contrato? [Consistency, Gap]
- [ ] CHK011 A fusão com o Joern é compatível com a regra de que o Joern segue rodando? [Consistency, Spec §FR-007]
- [ ] CHK012 As chaves por sistema (`taint.*`) são coerentes com o princípio V? [Consistency]

## Cenários e não funcionais

- [ ] CHK013 Há requisito para métodos recursivos e ciclos de chamada no rastreio? [Edge Case, Gap]
- [ ] CHK014 Está definido o comportamento com tempo esgotado no meio do rastreio? [Exception Flow, Spec §FR-010]
- [ ] CHK015 A mudança no job `benchmark` do `ci.yml` tem requisito de não alterar o resultado das outras categorias? [Dependency, Spec §FR-009]
- [ ] CHK016 Os campos novos opcionais do achado têm requisito de compatibilidade para clientes antigos? [Dependency, Gap]
- [ ] CHK017 Todo FR e SC tem pelo menos uma tarefa que o cobre? [Traceability]


## Notes

- Itens ficam desmarcados até a revisão do João; este comando não marca nenhum item.
- `/speckit-implement` lê o estado dos itens, mas não os altera.
