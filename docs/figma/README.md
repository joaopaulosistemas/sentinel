# Protótipos de telas do Sentinel (Figma)

Esta pasta guarda as telas do dashboard do Sentinel, prontas para levar ao Figma: uma **imagem** e um **HTML** de cada tela, nos temas claro e escuro, em janela de 1440 px de largura. Elas são geradas a partir do dashboard real (`audit-dashboard/`), com dados inventados, então sempre refletem o código atual.

## O que tem aqui

| Pasta ou arquivo | Conteúdo |
|---|---|
| `telas/` | PNG de cada tela inteira (`<tela>-claro.png`, `<tela>-escuro.png`). Servem de referência visual e podem ser arrastados para o Figma. |
| `html/` | HTML de cada tela, sem scripts, com o CSS usado pela tela e as fontes IBM Plex em `html/fonts/`. Abrem direto no navegador e entram no Figma com camadas editáveis. |
| `indice.json` | Lista de todas as capturas (tela, rota, tema, arquivo). |
| `capturar-telas.mjs` | Script que refaz tudo. |
| `dados-simulados.mjs` | Dados inventados que o script usa no lugar da API. Nenhum dado real entra aqui. |

## Telas

| Código | Tela | Rota | Arquivos |
|---|---|---|---|
| 01-visao-geral | Visão geral | `/` | `telas/01-visao-geral-claro.png` · `telas/01-visao-geral-escuro.png` |
| 01b-nova-auditoria | Visão geral (janela Nova auditoria) | `/` | `telas/01b-nova-auditoria-claro.png` · `telas/01b-nova-auditoria-escuro.png` |
| 02-sistemas | Sistemas (lista) | `/systems` | `telas/02-sistemas-claro.png` · `telas/02-sistemas-escuro.png` |
| 03-sistemas-novo | Sistemas (painel de cadastro) | `/systems` | `telas/03-sistemas-novo-claro.png` · `telas/03-sistemas-novo-escuro.png` |
| 04-sistemas-editar-cadastro | Sistemas (painel de edição: Cadastro e Git) | `/systems` | `telas/04-sistemas-editar-cadastro-claro.png` · `telas/04-sistemas-editar-cadastro-escuro.png` |
| 05-sistemas-editar-motor-de-regras | Sistemas (painel de edição: Motor de regras) | `/systems` | `telas/05-sistemas-editar-motor-de-regras-claro.png` · `telas/05-sistemas-editar-motor-de-regras-escuro.png` |
| 06-sistemas-editar-analisadores | Sistemas (painel de edição: Analisadores) | `/systems` | `telas/06-sistemas-editar-analisadores-claro.png` · `telas/06-sistemas-editar-analisadores-escuro.png` |
| 07-sistemas-editar-claude-mcp | Sistemas (painel de edição: Claude (MCP)) | `/systems` | `telas/07-sistemas-editar-claude-mcp-claro.png` · `telas/07-sistemas-editar-claude-mcp-escuro.png` |
| 08-detalhe-do-sistema | Detalhe do sistema | `/systems/1` | `telas/08-detalhe-do-sistema-claro.png` · `telas/08-detalhe-do-sistema-escuro.png` |
| 09-auditoria-em-execucao | Auditoria em execução | `/audits/102` | `telas/09-auditoria-em-execucao-claro.png` · `telas/09-auditoria-em-execucao-escuro.png` |
| 10-auditoria-resultado | Auditoria concluída (resultado) | `/audits/101` | `telas/10-auditoria-resultado-claro.png` · `telas/10-auditoria-resultado-escuro.png` |
| 11-achados | Achados | `/findings` | `telas/11-achados-claro.png` · `telas/11-achados-escuro.png` |
| 12-ranking | Ranking de sistemas | `/ranking` | `telas/12-ranking-claro.png` · `telas/12-ranking-escuro.png` |
| 13-relatorios | Relatórios | `/reports` | `telas/13-relatorios-claro.png` · `telas/13-relatorios-escuro.png` |
| 14-guia | Guia de código | `/guide` | `telas/14-guia-claro.png` · `telas/14-guia-escuro.png` |
| 15-configuracoes | Configurações | `/settings` | `telas/15-configuracoes-claro.png` · `telas/15-configuracoes-escuro.png` |

## Levar para o Figma

1. **Imagens:** arraste os PNG de `telas/` para o arquivo do Figma. Servem como referência para redesenhar.
2. **Camadas editáveis:** use um plugin que importa HTML, como o *html.to.design* (abra o HTML no navegador, capture com a extensão ou envie o arquivo pelo plugin). Cada elemento vira uma camada com auto-layout aproximado. Importe também o tema escuro, se quiser os dois.
3. **Fonte:** as telas usam IBM Plex Sans e IBM Plex Mono. Instale as duas no computador antes de importar, senão o Figma troca por outra fonte.

## Refazer as capturas

Depois de mudar uma tela do dashboard, na raiz do projeto:

```bash
cd audit-dashboard && npm ci && cd ..
node docs/figma/capturar-telas.mjs
```

O script sobe o dashboard (`npm run dev`), responde a API com `dados-simulados.mjs`, abre cada rota no Chrome e grava `telas/`, `html/` e `indice.json`. Não precisa de backend, de Docker nem de Maven. Se o Chrome não for achado sozinho, aponte o caminho em `E2E_CHROMIUM`. Para uma tela nova, acrescente uma linha na lista `telas` do script.

## Observação sobre fontes

O Carbon pede as fontes em `~@ibm/plex/...`, um caminho que o Vite em desenvolvimento não resolve; por isso, rodando `npm run dev`, o navegador usa a fonte do sistema. O script serve as fontes a partir de `node_modules/@ibm/plex` para as capturas saírem com o IBM Plex Sans do desenho original.
