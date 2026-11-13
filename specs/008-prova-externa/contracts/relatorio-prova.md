# Contrato: relatório de prova

Arquivo `docs/evolucao/relatorios/prova-<versão>.md` e `.json` irmão. Seções, nesta ordem: 1 Resumo; 2 Método; 3 Conjuntos (commit fixo, reproduzível, publicável); 4 Versões; 5 Números por pilha e categoria (taxa, n, IC 95 %, "sem medida"); 6 Treino × validação × reservado (diferença explicada); 7 Revisão externa (precisão por regra, concordância, divergências, pendentes, declaração de independência); 8 Comparação de mercado; 9 Aceite real; 10 Limites; 11 Verificado e NÃO verificado; 12 Histórico de correções.

Regras: toda taxa traz `n` e `ic95`; pilha sem medida diz "sem medida" e o que falta; nenhum trecho de código nem achado individual de repositório não publicável; resultado desfavorável publicado como os demais; o arquivo `.json` é determinístico (chaves ordenadas, `\n` final).

## Extensão pedida ao contrato de arquivos da spec 003

`audit-rules/calibracao/repositorios.json` ganha `publicavel` (booleano, obrigatório para entrar no relatório). A validação da 003 passa a recusar repositório sem o campo quando a 008 estiver ativa.

# Contrato: comando `scripts/prova.py`

```
prova.py montar        --versao <v> [--saida <pasta>]       # monta relatório .md e .json das saídas da 003
prova.py verificar     <relatorio.md>                       # seções, n e IC em toda taxa, publicabilidade, uma medição final por versão
prova.py amostra-externa --semente <int> --n <int> --saida <md>  # arquivo para o revisor, sem vereditos (usa medicao.py amostra --cego)
prova.py concordancia  --externa <md> --interna <revisao-humana.md>   # precisão por regra, concordância, divergências
prova.py comparar-mercado --ferramenta <id> --achados <json>          # mesma definição de acerto e alarme falso do benchmark
prova.py reproduzir    <relatorio.json>                     # refaz e compara; tempo dentro da tolerância declarada
prova.py aceite        --modelo                             # gera o modelo de evidência do aceite real
```

Códigos de saída: 0 ok; 1 falha de verificação (com a lista do que falhou); 2 uso incorreto. Nenhum comando executa código de repositório auditado.

# Contrato: arquivo de amostra externa (entregue ao revisor)

Tabela `| # | Repositório | Commit | Regra | Local (link) | Veredito | Nota |`. A coluna **Veredito** vem vazia; **nenhuma** coluna com veredito do agente ou do João. Só links de repositórios públicos. `prova.py verificar` falha se achar veredito interno no arquivo.
