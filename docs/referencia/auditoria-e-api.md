# Auditoria completa, automação, configurações e API

## Auditoria completa (áreas, dívida técnica e plano de ação)

Além da lista de findings, cada auditoria concluída gera uma visão no formato de uma auditoria profissional
(`GET /api/v1/audits/{id}/insights`, painel "Auditoria completa" na tela do sistema e seção no relatório HTML):

| Parte | O que traz |
|---|---|
| **Parecer e resumo executivo** | REPROVADO (há P0), ATENÇÃO (P1), APROVADO COM RESSALVAS (P2) ou APROVADO, com as áreas mais afetadas |
| **Por área** | 11 áreas (arquitetura, qualidade, segurança, testes, performance, frontend/acessibilidade, CI/CD e infra, documentação, dependências, observabilidade, manutenibilidade): estado, problemas, riscos, recomendações |
| **Dívida técnica** | Uma linha por problema: impacto, esforço (S/M/L), risco de regressão, ocorrências e prioridade **P0 Bloqueador** (crítico), **P1 Alta**, **P2 Importante**, **P3 Futura** |
| **Quick wins, Top 10 problemas, Top 10 melhorias** | Ordenados por gravidade × ocorrências ÷ esforço |
| **Plano de ação** | 8 fases: correções críticas, qualidade estrutural, performance, segurança, testes, UX/UI, DevOps, melhorias futuras |
| **Não foi possível verificar** | Tudo que a análise estática não alcança (cobertura real, Core Web Vitals, contraste, produção) e analisadores que não executaram |
| **Mapa do projeto** | Linguagens, frameworks, build, CI, Dockerfiles, testes, documentação — só o que foi observado nos arquivos |

Regras que sustentam isso:

- **Só o que foi comprovado.** O analisador de higiene (`sentinel-proj-*`) olha evidências de arquivos: sem README, sem testes, sem CI,
  `package.json` sem lockfile, `.env` ou chave privada versionados, `node_modules`/`target` commitados, Dockerfile sem `.dockerignore`.
  Ajuste ou desligue por perfil (`rules:`) e use os limites `min-source-files-for-tests` e `readme-min-lines`.
- **Área sem analisador não vira "ok".** Se nenhum analisador que cobre a área executou (ex.: Trivy e OWASP ausentes → dependências),
  a área aparece como *Não verificado* e o motivo entra na lista.
- **Esforço e risco vêm da remediação.** Em `remediation/*.yml` use `effort: S|M|L` e `regression-risk: LOW|MEDIUM|HIGH`.
  Sem eles, o esforço é M e aparece marcado como *estimado*.
- **Pacote novo, regra com teste.** `sentinel-pack validate --strict <pacote>` trata regra sem caso `// ruleid:` como erro (os pacotes embutidos passam).
- O mapa do projeto e a situação dos analisadores ficam na coluna `audit_run.context_json`; auditorias antigas, sem ela, geram a visão só com os findings.

## Auditoria automática, webhooks e notificações

**Periódica:** na tela do sistema, em *Auditoria automática*, defina o intervalo em horas e a branch. O agendador dispara quando vence e nunca empilha uma auditoria sobre outra em andamento.

**A cada push:** marque *Auditar a cada push* no sistema e cadastre o webhook no repositório:

| Plataforma | URL | Segredo |
|-----------|-----|---------|
| GitHub | `https://<sua-api>/api/v1/webhooks/github` (JSON, evento *push*) | campo *Secret* = segredo cadastrado no sistema (assinatura HMAC-SHA256) |
| GitLab | `https://<sua-api>/api/v1/webhooks/gitlab` | *Secret token* = segredo cadastrado no sistema |

O segredo é de cada sistema (tela Sistemas > Auditoria automática; há um botão "Gerar segredo"); sistema sem segredo não recebe webhook. Só a branch configurada no sistema dispara; tags e branches apagadas são ignoradas.

**Notificações:** na tela Sistemas, em *Notificações*, informe a URL do Incoming Webhook do Teams ou Slack (guardada cifrada; só https público), o formato (`teams`, `slack` ou `generic`), a queda mínima de nota e a severidade mínima dos achados novos. O aviso só sai quando há motivo: a auditoria falhou, a nota caiu o bastante ou surgiu achado novo da severidade escolhida (ou maior). Falha no envio nunca altera o resultado da auditoria.

## Tela de Configurações

O último item do menu (**Configurações**) reúne as variáveis que antes ficavam em `.env` e `application.properties`, com a explicação ao lado de cada uma: ferramentas de análise (ligar, tempo limite, máximo de achados, limites de clean code), pacotes de regras, Git e evidências. Há busca por nome, variável ou descrição.

- **O que se altera pela tela:** liga/desliga e limites das ferramentas, a configuração do ESLint (padrão do Sentinel, padrão da casa ou Google + Angular + Sonar), os pacotes desativados e de confiança, o laboratório de regras e os tempos do Git. Cada valor é validado antes de gravar.
- **O que fica só no ambiente (aparece em leitura):** banco de dados, `API_KEY`, `SECRETS_KEY`, CORS, login corporativo, caminhos do servidor (`CLONE_DIR`, `SENTINEL_LOCAL_ROOT`, `SENTINEL_EVIDENCE_DIR`, pastas de pacotes) e tudo que dá poder de executar programas (`PACKS_ALLOW_TOOLS`, repositório central de pacotes, `PMD_RULESET`, `SEMGREP_CONFIG`). Segredos aparecem mascarados. A API recusa alterá-los mesmo por chamada direta.
- **Quando vale:** a API lê as configurações ao subir. Salvar grava na tabela `app_setting`; o botão **Reiniciar a API** (ou um reinício normal do contêiner) aplica. O valor da tela tem prioridade sobre a variável de ambiente, que passa a ser só o valor inicial; "Voltar ao ambiente" apaga o valor da tela.
- **Sem banco ou sem a tabela** (primeira subida), a API sobe só com o ambiente. `SENTINEL_SETTINGS_FROM_DB=false` desliga a leitura da tela.
- **Reinício:** o endpoint encerra o processo e depende do `restart: unless-stopped` do Compose (ou do Kubernetes) para subir de novo. Quem roda a API à mão precisa subir de novo sozinho.
- **Certificados da empresa (CA)** continuam entrando na imagem pelo `audit-api/certs/` (veja o README dessa pasta); a tela ainda não instala certificados.

API: `GET /api/v1/settings`, `PUT /api/v1/settings` (`{"values": {"CHAVE": "valor"}}`; valor `null` volta ao ambiente), `DELETE /api/v1/settings/{chave}` e `POST /api/v1/settings/restart`. Como toda a API, exigem `API_KEY` quando ela está definida.

## Login corporativo (OIDC/Keycloak) — opcional

Guia completo, com usuários de teste, papéis e como criar usuários: [login com Keycloak](../guia/login-keycloak.md).

```bash
docker compose -f docker-compose.yml -f deploy/keycloak/docker-compose.yml up -d --build   # protótipo local
# ou, fora do Compose: mvn package -Poidc -f audit-api/pom.xml e
export QUARKUS_PROFILE=oidc OIDC_AUTH_SERVER_URL=https://keycloak.exemplo.com/realms/sentinel OIDC_CLIENT_ID=sentinel-api
```

A API valida assinatura, emissor e audiência (`OIDC_AUDIENCE`, padrão `sentinel-api`) e aplica dois papéis: `sentinel-admin` (tudo) e `sentinel-executor` (consulta e dispara auditorias; 403 em cadastros e configurações). `GET /api/v1/me` devolve o usuário e os papéis. `/api/v1/health` e os webhooks seguem públicos. **Limitação:** separa por papel, não por squad (isso exigiria filtrar sistemas pela claim do token).

---

## O contrato OpenAPI

O documento OpenAPI (`/q/openapi`) e o Swagger UI (`/swagger-ui`; ligado no Compose, em dev e teste; fora disso, `--build-arg SWAGGER_UI=true`) saem das anotações das rotas. A cada execução do CI, o job **Integração HTTP** grava o documento do build, valida a estrutura (`scripts/validar-openapi.py`) e o publica como artefato `openapi` (arquivo `openapi.json`) na página da execução. O teste de contrato falha se uma rota ficar sem descrição, resposta, erro documentado ou parâmetro descrito.

## Endpoints da API

| Método | Endpoint | Descrição |
|--------|---------|-----------|
| `GET` | `/api/v1/systems` | Lista todos os sistemas |
| `POST` | `/api/v1/systems` | Cadastra novo sistema |
| `PUT` | `/api/v1/systems/{id}` | Edita nome, descrição, repositório, stack, time e perfil (o agendamento tem endpoint próprio) |
| `POST` | `/api/v1/systems/check-repo` | Testa o repositório sem clonar (`git ls-remote --symref`): responde se está acessível, a branch padrão e se a branch pedida existe |
| `GET` | `/api/v1/systems/{id}` | Detalhe do sistema (inclui `scoreDelta`: nota da última auditoria completa menos a anterior; e `disabledSides`: lados `back`/`front` desligados na última auditoria concluída, `[]` quando os dois estavam ligados ou a auditoria é anterior ao campo. O resumo do `GET /api/v1/systems` traz o mesmo campo) |
| `DELETE` | `/api/v1/systems/{id}` | Remove o sistema (e suas supressões) |
| `PUT` | `/api/v1/systems/{id}/schedule` | Auditoria periódica / por push |
| `GET` | `/api/v1/systems/{id}/audits` | Histórico de auditorias do sistema |
| `GET` | `/api/v1/systems/{id}/trend` | Evolução do score |
| `GET` | `/api/v1/systems/{id}/suppressions` | Findings suprimidos (`DELETE …/{suppressionId}` reabre) |
| `POST` | `/api/v1/audits` | Dispara nova auditoria (assíncrono; PENDING → RUNNING → COMPLETED/FAILED/CANCELLED, com `errorMessage` em caso de falha) |
| `GET` | `/api/v1/audits?limit=` | Últimas auditorias de todos os sistemas (mais nova primeiro), com o andamento das que estão rodando |
| `POST` | `/api/v1/audits/{id}/cancel` | Cancela uma auditoria pendente ou em execução (`409` se já terminou) |
| `GET` | `/api/v1/audits/{id}` | Status e resultado da auditoria; enquanto roda, traz `progress` (etapa, analisadores e eventos) |
| `GET` | `/api/v1/audits/{id}/fix-plan` | O que corrigir primeiro: problemas por regra, com o ganho de nota de cada um |
| `GET` | `/api/v1/findings` | Achados da última auditoria concluída de cada sistema (filtros: `systemId`, `severity`, `category`, `status`, `onlyNew`, `includeSuppressed`, `limit`, `offset`) |
| `GET` | `/api/v1/audits/{id}/findings` | Findings (filtros: `severity`, `category`, `status`, `onlyNew`, `includeSuppressed`) Cada achado traz `codeContext` (as linhas reais do arquivo, de 2 antes a 4 depois de `lineNumber`) e `codeContextStart` (número da primeira linha do trecho); ficam nulos em segredos (nunca copiamos o valor), arquivo ausente ou binário e em auditorias antigas. `codeSnippet` não muda: é a identidade do achado entre auditorias |
| `GET` | `/api/v1/audits/{id}/diff` | Novos / resolvidos / persistentes vs. auditoria anterior |
| `GET` | `/api/v1/audits/{id}/report.pdf` | Mesmos relatórios em PDF (`type=` e `days=` iguais ao HTML) |
| `GET` | `/api/v1/audits/{id}/report` | Relatório HTML. `type=tecnico` (padrão, todos os achados), `executivo` (uma página: nota, meta, riscos e prioridades) ou `comparativo` (evolução entre auditorias; `days=` limita o período) |
| `GET` | `/api/v1/audits/{id}/findings.csv` | Achados ativos em CSV (UTF-8, protegido contra injeção de fórmula em planilha) |
| `GET` | `/api/v1/reports/portfolio` | Relatório de **todos os sistemas** (ou de um, com `systemId=`): `type=executivo\|tecnico\|comparativo`, `format=html\|pdf\|csv`, `days=` limita o período |
| `GET` | `/api/v1/audits/{id}/sbom` | SBOM CycloneDX 1.5 (JSON) |
| `GET` | `/api/v1/audits/{id}/insights` | Auditoria completa: parecer, áreas, dívida técnica P0–P3, quick wins, roadmap em 8 fases e o que não foi possível verificar |
| `GET` | `/api/v1/guide` · `/api/v1/guide/{tecnologia}/claude-md` | Guia de código por tecnologia (boas práticas, regras, nota) · CLAUDE.md gerado para a IA |
| `GET` | `/api/v1/packs` · `POST /reload` | Pacotes e perfis carregados (rejeitados com motivo, avisos, integridade) · recarrega |
| `PUT` | `/api/v1/systems/{id}/profile` | Perfil de regras do sistema |
| `POST` | `/api/v1/rules/playground` | Valida e executa uma regra Semgrep sobre um trecho de código |
| `GET` | `/api/v1/rules/quality` | Taxa de falso positivo por regra (a partir das supressões); marca as que merecem revisão |
| `POST` | `/api/v1/findings/{id}/suppress` | Suprime com motivo, justificativa e validade |
| `POST` | `/api/v1/findings/suppress-batch` | Suprime até 200 findings de uma vez (mesmo motivo/justificativa); devolve as falhas por id |
| `POST` | `/api/v1/webhooks/github` · `/gitlab` | Disparo por push (autenticado por segredo próprio) |
| `GET` | `/api/v1/systems/{id}/evidence` | Evidências offline do sistema (arquivos da pasta `evidencias/<sistema>/`); `PUT …/{nome}` envia um arquivo e `DELETE …/{nome}` apaga (valem a partir da próxima auditoria; veja [evidências](../evidencias/README.md)) |
| `GET` `PUT` | `/api/v1/engine-config/{id}` | Ajuste do motor do sistema (regras, limites, pacotes, analisadores) e o resultado já resolvido |
| `GET` `PUT` | `/api/v1/settings` | Configurações da instalação (tela de Configurações); `PUT` grava só as editáveis |
| `DELETE` | `/api/v1/settings/{chave}` | Volta uma configuração para o valor do ambiente |
| `POST` | `/api/v1/settings/restart` | Reinicia a API para as configurações salvas valerem |
| `GET` `PUT` | `/api/v1/mcp-access/{id}` | Acesso MCP do sistema: ligar, nível de dado, ferramentas; `POST/DELETE …/key` gera e revoga a chave; `GET …/calls` lista as chamadas |
| `POST` | `/mcp/{sistema}` | Servidor MCP (JSON-RPC), com a chave do sistema em `Authorization: Bearer`; fora da `API_KEY` global; limite de chamadas (`429`) |
| `GET` | `/api/v1/claude-review/{id}` | Sugestões e regras propostas do Claude; `…/metrics` mede quanto ele acerta; `POST …/suggestions/{sid}/status` e `…/rules/{rid}/review\|sample\|observe\|promote\|discard\|restore\|copy` movem o funil (`restore` desfaz um descarte) |
| `GET` | `/api/v1/pending-actions/{id}` | Pedidos do Claude; `POST …/{actionId}/approve` ou `…/reject`; `…/reopen` reabre um pedido recusado e `…/undo` desfaz a supressão de um aprovado |
| `GET` | `/api/v1/audits/{id}/iso25010` | Nota por característica da [ISO/IEC 25010](iso-25010.md), com características não avaliadas marcadas; `perfil=padrao\|financeiro\|interno` e `metas=security:90,...` |
| `GET` | `/api/v1/audits/{id}/score-explanation` | De onde vem a nota, por dimensão e regra, com o teto por regra marcado (20 pontos por regra em MEDIUM/LOW/INFO e 50 em HIGH; CRITICAL não tem teto); `without=<regras>` simula a nota sem elas |
| `GET` | `/api/v1/audits/{id}/freshness` | Auditoria desatualizada? Compara o commit auditado com a ponta atual da branch no remoto (`git ls-remote`) |
| `POST` | `/api/v1/systems/check-path` · `GET /api/v1/systems/local-source` | Confere uma pasta local dentro da raiz do servidor (é Git? branch, arquivos, alterações não commitadas) · informa se a origem "pasta local" está ligada |
| `GET` | `/api/v1/health` | Health check |

### Como ver a documentação da API (Swagger)

| Onde roda | Swagger UI | Documento OpenAPI |
|---|---|---|
| Modo dev (`mvn quarkus:dev`) | **http://localhost:8080/swagger-ui**, com o botão **Authorize** | http://localhost:8080/q/openapi |
| Compose | **http://localhost:8080/swagger-ui** (padrão; `SWAGGER_UI=false` no `.env` e `--build` tiram da imagem). A página é pública; as chamadas pedem a `API_KEY` pelo botão **Authorize** | http://localhost:8080/q/openapi?format=json |
| Imagem de produção fora do Compose | só com `--build-arg SWAGGER_UI=true` | http://localhost:8080/q/openapi?format=json |

Na imagem de produção o documento **exige a `API_KEY`** (só `/api/v1/health`, webhooks e MCP são livres):

```bash
curl -H "X-API-Key: $API_KEY" "http://localhost:8080/q/openapi?format=json" -o openapi.json
```

Para navegar nele com a interface do Swagger, importe o `openapi.json` no [Swagger Editor](https://editor.swagger.io), no
Postman ou no Insomnia. O CI também publica o documento de cada build como artefato `openapi`.

O documento OpenAPI (JSON em `/q/openapi?format=json`) declara as formas de autenticação: chave no cabeçalho `X-API-Key` ou `Authorization: Bearer <chave>` (a mesma `API_KEY` do servidor) e, só no MCP, a chave de acesso do sistema. No Swagger UI, **Authorize** guarda a chave uma vez para todas as chamadas. Saúde, webhooks e MCP não usam a chave global. Toda rota protegida documenta o 401, e os dois formatos de erro (`{"message": …}` e `{"error": …}`) estão em `components/schemas` (`ApiError`, `ApiKeyError`). Um teste de integração falha se uma rota ficar sem resumo, tag, resposta ou 401.

---

**Desenvolvido com ❤️ usando IBM Bob**  
**Bobathon de Modernização Java 2026**
