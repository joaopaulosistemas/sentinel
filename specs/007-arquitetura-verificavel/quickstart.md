# Roteiro de validação: spec 007

1. **Mapa** (SC-001): repositório multimódulo de teste; `arquitetura mapa` devolve 100 % dos módulos e dependências do gabarito, cada uma com evidência; classe sem sinal em "sem camada".
2. **Não resolvido** (SC-007): injeção por nome aparece como dependência "não resolvida".
3. **Desvio direto e indireto** (História 2): regra `web → dados`; uma classe web usa dados direto; outra passa por intermediário; ambos aparecem com caminho e evidência.
4. **Compatibilidade** (SC-004): o `architecture.yml` atual roda sem mudança e dá os mesmos achados de hoje.
5. **Exceção** (FR-009): vigente não conta como desvio e aparece com motivo e datas; vencida volta como desvio.
6. **Violações plantadas** (SC-002, SC-003): `arquitetura medir`; acerto e alarme falso com intervalo; repositório limpo sem acusação indevida; sem a régua da 003 não há "fica".
7. **PR** (SC-005): PR que cria uma violação e um ciclo; aparecem só esses.
8. **Tendência** (SC-006): duas versões, uma mais acoplada; a tendência aponta a piora; a identidade ignora mudança de linha.
9. **Sem declaração** (FR-010): projeto sem `architecture.yml` recebe o mapa, sem desvios, e o relatório sugere por onde começar.
10. **Peso** (FR-011): com o verificador em sombra, a nota de arquitetura não muda.
