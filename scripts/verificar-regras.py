#!/usr/bin/env python3
"""Confere a consistência das regras em audit-rules/packs (ver docs/referencia/severidade-das-regras.md).

Falha (saída 1) quando: há id de regra repetido; regra `category: security` sem `metadata.cwe`; um id de `order` de
dedup.yml não existe em nenhum pacote; um tópico `order` repete um id; um override de perfil (`profiles/*.yml`) aponta
para regra `sentinel-*` ou analisador Java (`arch-*`, `proj-*`...) que não existe; uma regra usa um CWE sem relação com o
que ela detecta (lista em CWES_SEM_RELACAO / CWE_1025_PERMITIDO).
"""
import collections
import fnmatch
import glob
import os
import re
import sys

import yaml

# CWEs que já foram usados por engano; "Binding to an Unrestricted IP" e "Excessive Server-Side Data Accesses" não
# descrevem estado estático mutável nem excesso de injeções. CWE-1025 só descreve comparação com fator errado.
CWES_SEM_RELACAO = {
    "CWE-1327": "Binding to an Unrestricted IP Address (use CWE-1058 para estado estático mutável)",
    "CWE-1060": "Excessive Number of Inefficient Server-Side Data Accesses (use CWE-1048 para acoplamento excessivo)",
}
CWE_1025_PERMITIDO = {"sentinel-ts-igualdade-frouxa"}

# Overrides de motores externos (eslint-*, jscpd-*, njsscan-*, checkstyle-*...) não são enumeráveis aqui.
_PREFIXOS_VERIFICADOS = ("sentinel-", "arch-", "proj-", "cc-")


def _carrega(caminho):
    with open(caminho, encoding="utf-8") as f:
        return yaml.safe_load(f)


def _ids_de_analisadores_java(raiz):
    """Ids literais ("arch-god-object") do código dos analisadores; insights/ fica de fora (tem prefixos de classificação)."""
    ids = set()
    padrao = os.path.join(raiz, "audit-api/src/main/java/com/sentinel/audit/analyzer/**/*.java")
    for f in glob.glob(padrao, recursive=True):
        with open(f, encoding="utf-8") as fh:
            ids |= set(re.findall(r'"([a-z][a-z0-9]*(?:-[a-z0-9.]+)+)"', fh.read()))
    return ids


def verificar(raiz="."):
    """Devolve (ids_das_regras, lista_de_erros) para o repositório em `raiz`."""
    erros = []
    ids = collections.defaultdict(list)
    for f in sorted(glob.glob(os.path.join(raiz, "audit-rules/packs/*/rules/*.yml"))):
        for r in (_carrega(f) or {}).get("rules", []) or []:
            ids[r["id"]].append(f)
            meta = r.get("metadata") or {}
            cwe = str(meta.get("cwe") or "")
            if meta.get("category") == "security" and not cwe:
                erros.append(f"{r['id']}: regra de segurança sem metadata.cwe ({f})")
            num = cwe.split(":")[0].strip().upper()
            if num in CWES_SEM_RELACAO:
                erros.append(f"{r['id']}: CWE incoerente {num} ({CWES_SEM_RELACAO[num]}) em {f}")
            if num == "CWE-1025" and r["id"] not in CWE_1025_PERMITIDO:
                erros.append(f"{r['id']}: CWE-1025 (Comparison Using Wrong Factors) só vale para regras de comparação ({f})")
    for i, fs in ids.items():
        if len(fs) > 1:
            erros.append(f"id repetido: {i} em {fs}")
    for f in sorted(glob.glob(os.path.join(raiz, "audit-rules/packs/*/dedup.yml"))):
        for t in _carrega(f) or []:
            lista = t.get("order") or []      # `rules` mistura ferramentas (CleanCode, PMD...): só `order` é de regras YAML
            if t.get("order") and len(set(lista)) != len(lista):
                erros.append(f"{f}: tópico {t.get('name')} repete id em order")
            for rid in lista:
                if rid not in ids:
                    erros.append(f"{f}: tópico {t.get('name')} cita regra inexistente {rid}")
    java = _ids_de_analisadores_java(raiz)
    conhecidos = set(ids) | java
    for f in sorted(glob.glob(os.path.join(raiz, "audit-rules/packs/profiles/*.yml"))):
        for chave in ((_carrega(f) or {}).get("rules") or {}):
            if not str(chave).startswith(_PREFIXOS_VERIFICADOS):
                continue
            if not any(fnmatch.fnmatchcase(i, str(chave)) for i in conhecidos):
                erros.append(f"{f}: override de perfil '{chave}' não corresponde a nenhuma regra nem analisador")
    return ids, erros


def main():
    ids, erros = verificar(".")
    print(f"{len(ids)} regras, {len(erros)} problema(s)")
    for e in erros:
        print(" -", e)
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
