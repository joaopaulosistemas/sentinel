# Roteiro de validação: spec 004

Pré-requisito: CI funcionando (hoje o Actions está sem cobrança; sem ele, só os testes locais de script rodam).

1. **Só o novo** (SC-001, SC-002): auditar o branch base; abrir PR que toca um arquivo com achados antigos e adiciona um; mover um trecho de lugar e reformatar outro. Esperado: comentário lista só o novo; trecho movido e reformatado não aparece; linha "N achados antigos fora deste comentário".
2. **Piorado** (FR-003): subir a severidade de uma regra e duplicar uma ocorrência. Esperado: ambos listados; só o excedente da duplicata.
3. **Sem base** (FR-003): PR contra branch sem auditoria completa. Esperado: aviso no comentário, comportamento atual por arquivo, PR não bloqueado.
4. **Código gerado** (FR-005): arquivo com cabeçalho de gerado com um achado de estilo e um de segurança. Esperado: estilo fora da nota e do comentário; segurança permanece.
5. **Faixa e agrupamento** (FR-008): comparar `ruleBand` com `precisao.md`; 30 achados da mesma causa viram 1 grupo de 30 locais com `groupBy=cause`.
6. **Não perdeu detecção** (SC-003, SC-006): rodar gabarito e OWASP Benchmark com as chaves ligadas e desligadas; recall igual (trava −0,02); `scripts/precisao-regras.py --verifica` aprova cada regra em sombra.
7. **Regressão** (FR-011): com todas as chaves desligadas, relatório e comentário idênticos aos de hoje.
