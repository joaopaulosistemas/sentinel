# Modelo de dados: Menos alarme falso e só o que é novo no PR

Nada aqui cria tabela nova. Tudo é estado derivado, campo opcional ou arquivo versionado.

## Baseline (estende `FindingLifecycleService.Baseline`)

| Campo | Tipo | Regra |
|---|---|---|
| fingerprints | conjunto de texto | já existe; identidade sem linha (regra + arquivo + trecho normalizado + ordinal) |
| severityByFingerprint | mapa fingerprint → severidade | novo; lido da mesma auditoria completa, sem coluna nova |
| countByRuleFile | mapa (regra, arquivo) → inteiro | novo; calculado dos achados da auditoria base |
| auditCommit | texto | já existe como `AuditRun.commitSha` (pode ser nulo → base indecidível) |

## ClasseNoPR (novo, calculado, não persistido)

| Valor | Quando | Vai ao comentário |
|---|---|---|
| NOVO | fingerprint ausente da base | sim |
| PIORADO | severidade maior que a da base, ou excedente de contagem da regra no arquivo | sim, só o excedente |
| EXISTENTE | fingerprint na base e não piorou | não; entra na linha "N achados antigos fora deste comentário" |
| INDECIDIDO | sem base para o arquivo (FR-003) | sim, com aviso |

Transições: nenhuma persistida; recalculada a cada auditoria de PR.

## RuleBand (novo, por regra)

| Campo | Tipo | Regra |
|---|---|---|
| ruleId | texto | chave |
| band | ALTA, MEDIA, BAIXA, SEM_MEDIDA | limite inferior de Wilson 95 %: ≥ 80 %, 50–79 %, < 50 %; amostra n < 20 ou regra corrigida depois da amostra → SEM_MEDIDA |
| sampleK, sampleN | inteiros | verdadeiros positivos e revisados |
| lowerBound | decimal 0–1 | limite inferior do IC |

Fonte: `audit-rules/calibracao/precisao-por-regra.json`, gerado de `docs/evolucao/precisao.md` por `scripts/precisao-regras.py --json`. Regra ausente do arquivo = SEM_MEDIDA.

## GrupoDeAchados (novo, calculado na leitura)

| Campo | Regra |
|---|---|
| chave | regra + trecho normalizado (fingerprint sem caminho) |
| contagem | número de achados com a chave |
| locais | arquivo e linha de cada um |
| severidade | a maior do grupo |

Nunca persistido; a nota continua contando cada achado.

## ExclusaoAutomatica (estende o que o `.sentinel.yml exclude` já mostra)

Motivo novo `GENERATED_NON_SECURITY`. Achado de categoria ≠ segurança em arquivo `PathRole.GENERATED` fica fora da nota e do comentário, e aparece no relatório com o motivo. Categoria vem do catálogo da regra.

## Chaves de configuração (por sistema, em `.sentinel.yml`; desligadas por padrão, FR-011)

| Chave | Efeito |
|---|---|
| `pr.classify` | liga ClasseNoPR, o comentário só com novo/piorado e o corte do SARIF |
| `generated.exclude-non-security` | liga a exclusão acima |
| `rule-band` | expõe `ruleBand` nos achados |

Lidas por `ProjectConfig`; nenhuma vai para `application.properties`.
