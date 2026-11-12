#!/usr/bin/env python3
"""Valida a estrutura do documento OpenAPI gerado pelo build (audit-api/target/openapi/openapi.json).

Confere: versão 3.x, título e versão da API, rotas, operationId único, toda operação com respostas
e toda referência "#/components/..." apontando para algo que existe. Sai com 1 e lista os problemas.
Uso: python3 scripts/validar-openapi.py [arquivo]
"""
import json
import sys

VERBOS = ("get", "post", "put", "delete", "patch", "head", "options")
PADRAO = "audit-api/target/openapi/openapi.json"


def referencias(no, achadas):
    if isinstance(no, dict):
        for chave, valor in no.items():
            if chave == "$ref" and isinstance(valor, str):
                achadas.append(valor)
            else:
                referencias(valor, achadas)
    elif isinstance(no, list):
        for item in no:
            referencias(item, achadas)


def resolve(doc, ref):
    if not ref.startswith("#/"):
        return None
    no = doc
    for parte in ref[2:].split("/"):
        if not isinstance(no, dict) or parte not in no:
            return None
        no = no[parte]
    return no


def problemas(doc):
    achados = []
    if not str(doc.get("openapi", "")).startswith("3."):
        achados.append("campo openapi ausente ou fora da versão 3.x")
    info = doc.get("info") or {}
    if not info.get("title") or not info.get("version"):
        achados.append("info.title e info.version são obrigatórios")
    paths = doc.get("paths") or {}
    if not paths:
        achados.append("o documento não tem rotas")
    ids = {}
    for caminho, item in paths.items():
        for verbo in VERBOS:
            op = item.get(verbo)
            if op is None:
                continue
            rota = f"{verbo.upper()} {caminho}"
            if not op.get("responses"):
                achados.append(f"{rota}: sem respostas")
            op_id = op.get("operationId")
            if op_id:
                if op_id in ids:
                    achados.append(f"{rota}: operationId repetido ({op_id}), também em {ids[op_id]}")
                ids[op_id] = rota
    refs = []
    referencias(doc, refs)
    for ref in sorted(set(refs)):
        if resolve(doc, ref) is None:
            achados.append(f"referência quebrada: {ref}")
    return achados


def contar(doc):
    return sum(1 for item in (doc.get("paths") or {}).values() for v in VERBOS if v in item)


def main(argv):
    arquivo = argv[1] if len(argv) > 1 else PADRAO
    try:
        with open(arquivo, encoding="utf-8") as f:
            doc = json.load(f)
    except (OSError, ValueError) as erro:
        print(f"não foi possível ler {arquivo}: {erro}")
        return 1
    achados = problemas(doc)
    for achado in achados:
        print(f"- {achado}")
    if achados:
        print(f"{len(achados)} problema(s) em {arquivo}")
        return 1
    print(f"OpenAPI válido: {contar(doc)} rotas, {len(doc.get('components', {}).get('schemas', {}))} esquemas")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
