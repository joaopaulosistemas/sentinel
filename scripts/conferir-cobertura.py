#!/usr/bin/env python3
"""Lê o jacoco.xml do Sentinel, mostra a cobertura de linhas e falha se ficar abaixo do mínimo.
Uso: conferir-cobertura.py <jacoco.xml> <minimo-em-%>   (mínimo 0 = só informa)"""
import sys
import xml.etree.ElementTree as ET


def linhas(caminho):
    # O relatório vem do nosso próprio build; ainda assim não resolve entidades externas.
    raiz = ET.parse(caminho).getroot()
    for c in raiz.findall("counter"):
        if c.get("type") == "LINE":
            return int(c.get("covered")), int(c.get("missed"))
    return 0, 0


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    cobertas, perdidas = linhas(sys.argv[1])
    total = cobertas + perdidas
    if total == 0:
        print("::error title=Cobertura::o relatório não tem contagem de linhas")
        return 1
    pct = 100.0 * cobertas / total
    minimo = float(sys.argv[2])
    print(f"::notice title=Cobertura de linhas::{pct:.1f}% ({cobertas} de {total}); mínimo {minimo:g}%")
    if pct < minimo:
        print(f"::error title=Cobertura abaixo do mínimo::{pct:.1f}% < {minimo:g}%")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
