# Contrato: formatos dos arquivos de dados

Todos em UTF-8, JSON com 1 espaço de indentação (como os gabaritos atuais), chaves ordenadas pelo gerador. Campo começado por `_` é comentário e é ignorado.

## `audit-rules/calibracao/repositorios.json`

```json
{
 "_comentario": "Um registro por repositório medido, fixado por commit.",
 "repositorios": [
  {
   "repo": "cr0hn/vulnerable-node",
   "grupo": "treino",
   "origem": "proposital",
   "pilhas": ["JAVASCRIPT"],
   "commit": "0123456789abcdef0123456789abcdef01234567"
  }
 ]
}
```

## `audit-rules/calibracao/propostas/<id>.json`

```json
{
 "id": "006-rastreio-sqli",
 "titulo": "Rastreio entre arquivos para injeção de SQL",
 "spec": "006",
 "linha_de_base": "20261112-linha-de-base-treino",
 "semente": 20261112,
 "criterios": {
  "metrica": "sqli",
  "ganho_minimo": 0.05,
  "alarme_falso_max_aumento": 0.0,
  "tempo_max_aumento_pct": 20,
  "tolerancia_categoria": 0.02
 }
}
```

## Medição (`docs/evolucao/medicao/<id>.json`), trecho

```json
{
 "id": "20261112-linha-de-base-treino",
 "conjunto": "treino",
 "versao_sentinel": "12.96.0",
 "versoes_ferramentas": {"opengrep": "x.y.z", "joern": null},
 "proposta_sha256": null,
 "por_pilha": {
  "JAVA": {"estado": "medida", "categorias": ["sqli", "xss"]},
  "KUBERNETES": {"estado": "sem-medida", "motivo": "nenhum repositório com gabarito"}
 },
 "categorias": {
  "sqli": {
   "casos": 120, "achados": 90, "tp": 80, "fp": 10,
   "taxa_acerto": {"valor": 0.667, "ic95": [0.58, 0.75], "n": 120, "amostra_pequena": false},
   "taxa_alarme_falso": {"valor": 0.083, "ic95": [0.05, 0.14], "n": 120, "amostra_pequena": false}
  }
 },
 "tempo": {"agregado_s": 1234, "por_repositorio": {"cr0hn/vulnerable-node": {"duracao_s": 41, "arquivos": 87}}},
 "excluidos": {"codigo_gerado": 12, "terceiros": 340},
 "verificado": ["..."],
 "nao_verificado": ["..."]
}
```

(Os números acima são só de formato, não são medidas.)

## Veredito, trecho

```json
{
 "proposta": "006-rastreio-sqli",
 "proposta_sha256": "…",
 "resultado": "fica",
 "pos_medida": false,
 "recusado": false,
 "ganho": {"valor": 0.07, "ic95": [0.02, 0.12]},
 "criterios": [
  {"nome": "ganho_minimo", "ok": true, "valor": 0.07},
  {"nome": "alarme_falso", "ok": true, "valor": 0.0},
  {"nome": "tempo", "ok": true, "valor": 12}
 ]
}
```

## `audit-rules/calibracao/reservado/consultas.json`

```json
{
 "_comentario": "Registro só-acrescenta: linhas existentes nunca são editadas nem apagadas.",
 "consultas": [
  {"data": "2026-11-12", "versao_sentinel": "12.96.0", "marco": "marco-0-linha-de-base", "motivo": "linha de base antes do Premium", "medicao": "20261112-linha-de-base-reservado"}
 ]
}
```

## Regras de validação (cobertas por testes)

1. `repositorios.json` e `conjuntos.json` listam os mesmos repositórios, cada um em um grupo só.
2. `pilhas` só aceita valores do escopo fixo; vazio é erro.
3. `commit` tem 40 caracteres hexadecimais.
4. Gabarito de `validacao` e de `reservado` tem `fonte` em cada falha.
5. Nome de repositório do reservado só aparece nos arquivos permitidos (conjuntos, repositórios, gabarito do reservado, consultas, medições e relatórios de contagens, estado).
6. `consultas.json` só cresce (comparação com a base do PR).
7. Medição do reservado exige `marco`.
8. Taxa sem `n` ou sem `ic95` invalida o relatório.
