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
    return {_normaliza(c) for c in saida}


def _normaliza(comando):
    """O pg_dump escreve o mesmo CHECK de enum de dois jeitos (ANY (ARRAY[...]) ou ANY ((ARRAY[...])::text[])).
    Reduz cada CHECK à coluna e aos valores, que é o que importa."""
    def reduz(m):
        valores = ",".join(sorted(re.findall(r"'([^']*)'", m.group(3))))
        return "CONSTRAINT %s CHECK (%s IN (%s))" % (m.group(1), m.group(2), valores)
    return re.sub(r"CONSTRAINT (\w+) CHECK \(\(\((\w+)\)::text = ANY (.*?\]\)?(?:::text\[\])?\))\)\)", reduz, comando)


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
    # Detalhe por coluna, para achar rápido o que difere dentro de uma tabela.
    for c in so_banco:
        m = re.match(r"CREATE TABLE (\S+) \((.*)\)$", c)
        par = next((v for v in so_v1 if v.startswith("CREATE TABLE %s " % m.group(1))), None) if m else None
        if m and par:
            a = [x.strip() for x in m.group(2).split(", ")]
            b = [x.strip() for x in re.match(r"CREATE TABLE \S+ \((.*)\)$", par).group(1).split(", ")]
            print("  %s: só no banco=%s | só no V1=%s | ordem igual=%s" % (m.group(1), [x for x in a if x not in b], [x for x in b if x not in a], [x for x in a if x in b] == [x for x in b if x in a]))
    return 1 if so_banco or so_v1 else 0


if __name__ == "__main__":
    sys.exit(main())
