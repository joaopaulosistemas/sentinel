#!/usr/bin/env python3
"""Confere que o esquema do banco depois da subida da API (Flyway + Hibernate) é igual ao V1 do Flyway.
Se o Hibernate ainda precisou criar ou alterar algo, falta uma migração: o script lista a diferença e sai com 1.
Uso: conferir-esquema.py <pg_dump.sql> <V1__baseline.sql>"""
import re
import sys


def comandos(caminho):
    texto = open(caminho, encoding="utf-8").read()
    texto = re.sub(r"--[^\n]*", "", texto)
    saida = set()
    for c in texto.split(";"):
        c = " ".join(c.split())
        if not c or c.startswith(("SET ", "SELECT pg_catalog")) or "flyway_schema_history" in c or c.startswith("\\"):
            continue
        saida.add(c)
    return saida


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    banco, v1 = comandos(sys.argv[1]), comandos(sys.argv[2])
    so_banco, so_v1 = sorted(banco - v1), sorted(v1 - banco)
    for c in so_banco:
        print("Só no banco (falta migração):", c[:300])
    for c in so_v1:
        print("Só no V1:", c[:300])
    return 1 if so_banco or so_v1 else 0


if __name__ == "__main__":
    sys.exit(main())
