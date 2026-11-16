# Observabilidade: métricas, logs, traces e painel no Grafana

Camada opcional que mostra, sem abrir o código, **o que a API está fazendo e por que**: está saudável? qual analisador está
lento ou falhando? por onde passou uma requisição? Os detalhes técnicos (desenho, configuração de cada peça, limites) estão em
[`deploy/observabilidade/README.md`](../../deploy/observabilidade/README.md). Aqui está o uso.

## Subir

```bash
# na raiz do repositório, uma linha só
docker compose -f docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d --build
```

Defina `API_KEY` no `.env`. Sem ela, e sem OIDC, a API **recusa subir** em produção; só para desenvolvimento local use
`ALLOW_OPEN_API=true`. Tudo escuta apenas em `127.0.0.1`.

| O quê | Endereço | Acesso |
|---|---|---|
| Grafana (painel, logs, traces) | http://localhost:3001 | `admin` e `GRAFANA_PASSWORD` (padrão `sentinel`) |
| Prometheus (métricas e alertas) | http://localhost:9090 | sem login |
| Alertmanager (avisos) | http://localhost:9093 | sem login |

## Usar o Grafana

1. **Gere dados.** Rode uma auditoria; antes disso os gráficos ficam vazios. As métricas chegam em até ~15 s (intervalo de coleta).
2. **Painel pronto:** **Dashboards > Sentinel — Visão geral** (14 painéis): saúde e erros da API, tempo por analisador e por
   auditoria, achados por analisador, memória, CPU e conexões do banco. Ajuste o período e a atualização automática no canto superior direito.
3. **Investigar** em **Explore**:
   - **Tempo (traces):** filtre por `service.name = sentinel-audit-api`; cada trace mostra o tempo de cada etapa.
   - **Loki (logs):** `{service_name="sentinel-audit-api"}`; acrescente `|= "ERROR"` para filtrar erros.
   - **Do trace aos logs:** no trace, **Logs for this span** abre os logs daquela requisição (cada linha carrega o `trace_id`).
4. **Métricas cruas:** Explore com a fonte Prometheus, digitando `sentinel_` para listar as métricas do projeto.

Se um painel mostrar "No data", confira o período e se já houve uma auditoria.

## Métricas do Sentinel

| Métrica | Rótulos | Serve para |
|---|---|---|
| `sentinel_analyzer_duration_seconds` | `analyzer`, `status` (OK, PARCIAL, FALHOU) | qual ferramenta está lenta ou falhando |
| `sentinel_findings_total` | `analyzer` | quantos achados cada ferramenta produz |
| `sentinel_audit_duration_seconds` | `outcome` (COMPLETED, FAILED, CANCELLED) | tempo e taxa de falha das auditorias |

Além delas, a API expõe as métricas do Quarkus (`http_server_requests_*`, `jvm_*`, `agroal_*`). Ficam na **porta de gestão 9000**
(`/q/metrics`), separada da API e **não publicada** fora da rede do Compose. Os rótulos têm poucos valores de propósito (nunca
repositório ou id de auditoria), para não pesar o Prometheus.

## Alertas

São 5 regras em `deploy/observabilidade/alertas.yml`: API fora do ar, analisador falhando 3 vezes em 30 min, mais de 20% de
auditorias falhas na hora, heap acima de 90% e erros 5xx. Estado em http://localhost:9090/alerts. O Alertmanager agrupa e reenvia
a cada 4 h; um alerta crítico inibe os de aviso da mesma instância.

Para receber no **Slack** ou no **Teams**, escolha o receptor e guarde a URL do webhook num arquivo seu, fora do repositório:

```bash
echo 'https://hooks.slack.com/services/...' > ~/sentinel-webhook.txt
ALERTMANAGER_CONFIG=./deploy/observabilidade/alertmanager-slack.yml \
ALERT_WEBHOOK_FILE=~/sentinel-webhook.txt \
docker compose -f docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d
```

| Canal | `ALERTMANAGER_CONFIG` |
|---|---|
| Webhook genérico (padrão) | `alertmanager.yml` |
| Slack | `alertmanager-slack.yml` |
| Microsoft Teams | `alertmanager-teams.yml` |

## Rastreamento (OpenTelemetry)

Vem **desligado** (`OTEL_SDK_DISABLED=true`) e o overlay o liga. Só o `baggage` é propagado: o `traceparent` recebido não é
extraído, por causa da CVE-2026-118259 no OpenTelemetry 1.62.0 (ver [LOG](../evolucao/LOG.md)). Cada requisição abre um trace novo.
Para apontar para outro coletor: `OTEL_EXPORTER_OTLP_ENDPOINT`.

## Limites

Protótipo verificado pelo CI (workflow *Observabilidade*): a pilha sobe, uma auditoria completa e métricas, logs e traces chegam
ao Grafana. Não há alta disponibilidade, os dados ficam em volumes locais e não existe versão Helm da pilha.
