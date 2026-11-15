# Camada de observabilidade do Sentinel (protótipo)

Observabilidade é conseguir responder, olhando de fora, **o que o sistema está fazendo e por que** — sem abrir o código.
Ela se apoia em três tipos de sinal, e esta camada junta os três num lugar só.

| Sinal | Pergunta que responde | Quem produz | Quem guarda | Onde se vê |
|---|---|---|---|---|
| Métricas | "Está saudável? Quão rápido? Quanto?" | Micrometer (no Quarkus) | Prometheus | painel do Grafana |
| Logs | "O que aconteceu?" | OpenTelemetry (logs) | Loki | painel e Explore |
| Traces | "Por onde a requisição passou e onde demorou?" | OpenTelemetry (traces) | Tempo | Explore > Tempo |

## Como subir

```bash
export POSTGRES_PASSWORD=...   # o mesmo do .env do Sentinel
docker compose -f docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d
```

- Grafana: <http://localhost:3001> (usuário `admin`, senha `GRAFANA_PASSWORD`, padrão `sentinel`). Painel **Sentinel — visão geral**.
- Prometheus: <http://localhost:9090> (alertas em *Alerts*).
- As portas só abrem em `127.0.0.1`. Para acesso remoto, ponha um proxy reverso com autenticação na frente do Grafana.

## Desenho

```
audit-api ── métricas (porta 9000, interna) ◄── Prometheus ──┐
    │                                                        ├──► Grafana (painel único)
    └── OTLP: traces e logs ──► OpenTelemetry Collector ─┬──► Tempo (traces) ──┤
                                                         └──► Loki  (logs) ────┘
```

**Por que um Collector no meio?** A aplicação só fala OTLP com um endereço. Trocar Tempo e Loki por Datadog, Elastic ou
Jaeger vira mudança no `otel-collector.yaml`, sem recompilar nem reiniciar a API.

**Por que a porta 9000?** As métricas saem numa porta de gerenciamento separada da API (8080). Assim a chave da API
não precisa abrir exceção para o Prometheus e a porta nunca é publicada no host: só a rede do Docker enxerga.

## O que o Sentinel mede

Do Quarkus (infraestrutura): `http_server_requests_*` (requisições por rota e status), `jvm_*` (heap, GC, threads),
`process_cpu_usage`, `agroal_*` (pool de conexões do banco).

Do Sentinel (negócio, `AuditMetrics`):

| Métrica | Rótulos | Para que serve |
|---|---|---|
| `sentinel_analyzer_duration_seconds` | `analyzer`, `status` (OK, PARCIAL, FALHOU) | qual ferramenta está lenta ou falhando |
| `sentinel_findings_total` | `analyzer` | quantos achados cada ferramenta produz |
| `sentinel_audit_duration_seconds` | `outcome` (COMPLETED, FAILED, CANCELLED) | tempo e taxa de falha das auditorias |

Só rótulos de poucos valores. Nunca o repositório nem o id da auditoria: cada valor novo cria uma série nova e
derruba o Prometheus (a "explosão de cardinalidade").

## Alertas de exemplo (`alertas.yml`)

API fora do ar, analisador falhando 3 vezes em 30 min, mais de 20% de auditorias falhas na hora, heap acima de 90%,
erros 5xx.

O **Alertmanager** (<http://localhost:9093>) agrupa os alertas e avisa por **webhook** (serve para Slack, Teams, n8n...).
A URL fica num arquivo seu, fora do repositório:

```bash
echo 'https://hooks.slack.com/services/...' > ~/sentinel-webhook.txt
ALERT_WEBHOOK_FILE=~/sentinel-webhook.txt docker compose -f docker-compose.yml -f deploy/observabilidade/docker-compose.yml up -d
```

Sem `ALERT_WEBHOOK_FILE` usa-se `webhook-url.exemplo`, que não entrega nada. Se a API cair, os alertas de aviso são
inibidos para não gerar ruído.

O webhook genérico manda o JSON do próprio Alertmanager. Para **Slack** ou **Teams** use o receptor nativo, escolhido por
`ALERTMANAGER_CONFIG` (o arquivo com a URL continua sendo `ALERT_WEBHOOK_FILE`):

| Canal | `ALERTMANAGER_CONFIG` | O que vai em `ALERT_WEBHOOK_FILE` |
|---|---|---|
| Webhook genérico (padrão) | `alertmanager.yml` | URL do seu receptor (n8n, etc.) |
| Slack | `alertmanager-slack.yml` | URL do Incoming Webhook do Slack |
| Microsoft Teams | `alertmanager-teams.yml` | URL do webhook do canal |

## Verificação

`scripts/verificar-observabilidade.sh` (rodado no CI, workflow `Observabilidade`) sobe a pilha, roda uma auditoria
pequena e confere: alvo `up` no Prometheus, métricas de negócio e de JVM, regras de alerta, painel provisionado, as
três fontes de dados saudáveis, **todas as consultas do painel válidas**, logs no Loki e traces no Tempo.

## Limites do protótipo

- Alertmanager com webhook, Slack e Teams (sem e-mail), sem autenticação própria nos componentes, sem retenção longa (15 dias de métricas, 7 de logs e traces).
- Armazenamento local em volumes do Docker; em produção use armazenamento de objetos (S3) para Loki e Tempo.
- No Kubernetes (Helm) a mesma ideia vale, mas os componentes entram por charts próprios; não está neste protótipo.
