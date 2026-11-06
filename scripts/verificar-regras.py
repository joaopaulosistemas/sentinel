#!/usr/bin/env python3
"""Confere a consistência das regras em audit-rules/packs (ver docs/referencia/severidade-das-regras.md).

Falha (saída 1) quando: há id de regra repetido; regra `category: security` sem `metadata.cwe`; um id de `order` de
dedup.yml de `order` não existe em nenhum pacote; um tópico `order` repete um id.
"""
import glob, sys, yaml, collections

erros = []
ids = collections.defaultdict(list)
for f in sorted(glob.glob("audit-rules/packs/*/rules/*.yml")):
    for r in (yaml.safe_load(open(f, encoding="utf-8")) or {}).get("rules", []) or []:
        ids[r["id"]].append(f)
        meta = r.get("metadata") or {}
        if meta.get("category") == "security" and not meta.get("cwe"):
            erros.append(f"{r['id']}: regra de segurança sem metadata.cwe ({f})")
for i, fs in ids.items():
    if len(fs) > 1:
        erros.append(f"id repetido: {i} em {fs}")
for f in sorted(glob.glob("audit-rules/packs/*/dedup.yml")):
    for t in yaml.safe_load(open(f, encoding="utf-8")) or []:
        lista = t.get("order") or []          # `rules` mistura ferramentas (CleanCode, PMD...): só `order` é de regras YAML
        if t.get("order") and len(set(lista)) != len(lista):
            erros.append(f"{f}: tópico {t.get('name')} repete id em order")
        for rid in lista:
            if rid not in ids:
                erros.append(f"{f}: tópico {t.get('name')} cita regra inexistente {rid}")
print(f"{len(ids)} regras, {len(erros)} problema(s)")
for e in erros:
    print(" -", e)
sys.exit(1 if erros else 0)
