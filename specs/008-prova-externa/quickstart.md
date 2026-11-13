# Roteiro de validação: spec 008

1. **Relatório repetível** (SC-001): `prova.py montar` duas vezes dá bytes idênticos; `prova.py reproduzir` por quem não participou do projeto devolve os mesmos números (tempo dentro da tolerância declarada).
2. **Toda taxa com n e IC** (SC-003): `prova.py verificar` falha com taxa sem `n` ou `ic95`; pilha sem medida diz "sem medida" e o que falta.
3. **Amostra cega** (História 2): `prova.py amostra-externa` não contém veredito do agente nem do João; a mesma semente dá a mesma amostra.
4. **Concordância** (SC-002): com vereditos de teste, a precisão por regra e a concordância saem corretas e cada divergência é listada; abaixo de 80 %, as regras divergentes aparecem como "sem evidência". Com o revisor real, **só vale quando ele concluir**.
5. **Reservado** (SC-004): o registro mostra uma medição final por versão; o relatório traz o total de consultas.
6. **Comparação** (SC-005): pelo menos duas ferramentas, mesmos conjuntos, versões fixadas, mapa de regras commitado antes; resultado desfavorável publicado.
7. **Aceite real** (SC-006): **ação do João**; evidência anexada (link da execução e alertas exportados).
8. **Publicabilidade** (SC-007): `prova.py verificar` acusa trecho de código ou achado individual de repositório não publicável; 0 no relatório final.
9. **Correção** (FR-012): trocar um número publicado sem registro no histórico é recusado.
