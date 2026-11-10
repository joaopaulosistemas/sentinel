#!/usr/bin/env python3
"""Confere que o esquema do banco depois da subida da API (Flyway + Hibernate) é igual ao V1 do Flyway.
Se o Hibernate ainda precisou criar ou alterar algo, falta uma migração: o script lista a diferença e sai com 1.
Uso: conferir-esquema.py <pg_dump.sql> <V1__baseline.sql> [V2.sql V3.sql ...]
As migrações depois do V1 só entram pelo que mudam no esquema: DROP INDEX e ALTER COLUMN ... TYPE timestamp(6) with time zone."""
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


def _aplica_fuso(v1, comando):
    """ALTER TABLE t ALTER COLUMN c TYPE timestamp(6) with time zone: a coluna do CREATE TABLE do V1 passa a ter fuso."""
    m = re.match(r"ALTER TABLE (?:ONLY )?(?:public\.)?(\w+) (.*)$", comando)
    if not m:
        return v1
    colunas = re.findall(r"ALTER COLUMN (\w+) TYPE timestamp\(6\) with time zone", m.group(2))
    if not colunas:
        return v1
    novo = set()
    for x in v1:
        if re.match(r"CREATE TABLE (?:public\.)?%s " % m.group(1), x):
            for col in colunas:
                x = re.sub(r"\b%s timestamp\(6\) without time zone" % col, "%s timestamp(6) with time zone" % col, x)
        novo.add(x)
    return novo


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    banco, v1 = comandos(sys.argv[1]), comandos(sys.argv[2])
    for extra in sys.argv[3:]:
        for c in comandos(extra):
            m = re.match(r"DROP INDEX (?:IF EXISTS )?(?:public\.)?(\w+)$", c)
            if m:
                v1 = {x for x in v1 if not re.match(r"CREATE (?:UNIQUE )?INDEX %s ON " % m.group(1), x)}
            v1 = _aplica_fuso(v1, c)
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
