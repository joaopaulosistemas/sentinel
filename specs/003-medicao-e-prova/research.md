# Pesquisa (Fase 0): Sentinel Premium 1 — Medição e prova

Decisões técnicas tomadas para o plano. Não há `NEEDS CLARIFICATION` pendente. Fatos sobre o código foram levantados em 2026-11-12.

## D1. A medição é ferramenta do repositório, em Python, não funcionalidade da API

- **Decisão**: um único script `scripts/medicao.py` (somente biblioteca padrão do Python 3), com subcomandos, mais dados em JSON e relatórios em Markdown/JSON versionados. Nenhuma mudança em Java, no banco ou no painel nesta spec.
- **Por quê**: toda a medição que existe hoje já é assim (`benchmark-owasp.py`, `medir-volta.py`, `comparar-calibracao.py`, `precisao-regras.py`, `concordancia-revisao.py`, `verificar-separacao.py`); roda no runner do GitHub e **também nesta sessão**, o que permite provar a lógica aqui com testes de unidade, algo que não é possível para Java (princípio VIII). A spec diz que o painel está fora do escopo.
- **Alternativas**: serviço na API Quarkus com tabela no PostgreSQL (rejeitada: não verificável aqui, mistura ferramenta de desenvolvimento com produto e exigiria migração e contrato de API); planilha manual (rejeitada: não reproduzível).
- **Limite conhecido**: com `python3 -I` o diretório do script não entra no caminho de importação; por isso o arquivo é único e os testes carregam o módulo pelo caminho (o mesmo cuidado já tomado em `test_validar_openapi.py`).

## D2. Cada repositório medido é fixado por commit

- **Decisão**: `audit-rules/calibracao/repositorios.json` guarda, para cada repositório de qualquer grupo, `commit`, `origem` (`proposital` ou `real`), `pilhas` e `grupo`. Quem mede busca exatamente aquele commit.
- **Por quê**: hoje a calibração clona a branch padrão, e o resultado muda quando o repositório muda (FR-004 e SC-002 não seriam possíveis). Repositório que desaparece vira medição "não reproduzível", não zero.
- **Alternativa**: guardar só a data (rejeitada: não identifica o código).

## D3. Estatística

- **Taxa simples** (acerto, alarme falso, precisão): intervalo de Wilson de 95%, já usado em `medir-volta.py`. Não se altera esse script: o novo reimplementa a fórmula e o teste confere os dois contra os mesmos valores.
- **Antes × depois nos mesmos casos**: o resultado por caso é binário e **pareado**; o intervalo da diferença sai de *bootstrap* pareado (10.000 reamostragens, semente fixa gravada na proposta), determinístico e sem dependências.
- **Amostra pequena**: menos de 20 casos recebe o aviso (valor da premissa da spec; fica em constante única, ajustável).
- **Veredito**: "fica" só se todos os critérios forem cumpridos **e** o limite inferior do intervalo do ganho for maior que zero; "sai" se alguma categoria piorar acima da tolerância ou o custo passar do aceito; senão, "inconclusivo".
- **Alternativa**: teste de significância com valor-p (rejeitada: o intervalo responde à pergunta prática e é mais fácil de explicar).

## D4. Critérios escritos antes de medir

- **Decisão**: cada mudança sob avaliação tem um arquivo `audit-rules/calibracao/propostas/<id>.json` com os critérios (métrica, ganho mínimo, aumento aceito de alarme falso e de tempo, semente). O comando calcula o `sha256` do arquivo; a medição grava esse hash; o veredito compara. Medição sem hash, ou com hash diferente do arquivo atual, vira "pós-medida" e não pode dar "fica".
- **Por quê**: prova mecânica de que o critério existia quando se mediu, sem depender de confiança.
- **Alternativa**: confiar na ordem dos commits (rejeitada: reescrevível e difícil de automatizar).

## D5. Conjunto reservado: o que o CI consegue e o que não consegue garantir

- **Decisão**: novo grupo `reservado` em `conjuntos.json`, com lista fixa, hash da composição em `docs/evolucao/estado.json` (mesmo mecanismo do treino e da validação) e novas checagens em `verificar-separacao.py`: (5) um repositório em um único grupo, considerando o reservado; (6) gabarito do reservado só em `gabaritos/reservado/` e com `fonte` em cada falha; (7) nome de repositório reservado só aparece em arquivos de uma lista permitida (conjuntos, gabarito, registro de consultas, relatório de contagens); (8) o registro de consultas só recebe linhas novas (nunca edita nem apaga).
- **Limite honesto**: o CI **não consegue** saber se uma pessoa ou agente abriu o código na própria máquina. A garantia é sobre o que fica versionado, e o resto é regra de conduta já escrita na constituição (princípio III) e no `LEIAME` do grupo. Isso vai para a seção "NÃO verificado" do relatório.
- **Alternativa**: criptografar o conjunto (rejeitada: complexidade sem ganho real, o código é público).

## D6. Tempo e tamanho por auditoria

- **Decisão**: `AuditRun` já tem `startedAt` e `completedAt`; o script `scripts/auditoria-completa.sh` passa a escrever no resumo a duração e a contagem de arquivos de código. O `medicao.py` lê esses campos do resumo.
- **Por quê**: não exige mudar a API; é aditivo.

## D7. Pilha de cada repositório

- **Decisão**: o atributo `pilhas` em `repositorios.json` (uma ou mais entre as do escopo fixo da constituição, princípio VI). O relatório agrupa por pilha; pilha do escopo sem nenhum repositório com gabarito é "sem medida".
- **Por quê**: hoje a pilha só existe como argumento do workflow, e o `estado.json` guarda só a última.

## D8. Onde o relatório vive

- **Decisão**: `docs/evolucao/medicao/` com `linha-de-base.json` (máquina) e `linha-de-base.md` (pessoa), mais `consultas-reservado.json` em `audit-rules/calibracao/reservado/`. Relatório é artefato do job e também commitado quando o João aprova.
- **Por quê**: consultável sem rodar nada; o `conferir-numeros.py` pode, depois, conferir números citados em documentos.

## D9. Fonte de código real para precisão

- **Decisão**: lista `audit-rules/calibracao/codigo-real.json` de projetos abertos reais (não vulneráveis de propósito), em Java/Spring e Angular primeiro; amostra sorteada com semente do arquivo `precisao`/`revisao-humana.md` existente (mesmo formato de linha, para reaproveitar `concordancia-revisao.py`).
- **Seleção dos repositórios**: por metadados públicos (linguagem, tamanho, atividade), sem ler código; **a lista final é aprovada pelo João**.

## D10. O que não entra neste plano

- Painel, API e banco; execução de código auditado; qualquer mudança de regra ou de nota; ferramentas de terceiros novas.

## Fontes levantadas no código

`scripts/medir-volta.py` (Wilson, recall por gabarito, comparar), `scripts/benchmark-owasp.py` (TPR, FPR, baseline, tolerância), `scripts/verificar-separacao.py` (checagens 1 a 4), `scripts/concordancia-revisao.py`, `scripts/precisao-regras.py`, `audit-rules/calibracao/{conjuntos.json,gabaritos,baselines,benchmark}`, `docs/evolucao/{PLACAR.md,estado.json,precisao.md,revisao-humana.md}`, `audit-api/.../AuditRun.java` (campos de tempo), `.github/workflows/{ci.yml,auditoria-completa.yml}`.
