#!/usr/bin/env python3
"""Fonte única da versão: audit-dashboard/package.json.

Propaga a versão para as tags de imagem do chart Helm, o appVersion do chart e a versão publicada no OpenAPI.
Sem argumentos, reescreve os arquivos; com --check apenas confere (usado no CI) e sai com 1 se algo divergir.
"""
import json
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

ALVOS = [
    (RAIZ / "deploy/helm/sentinel/values.yaml",
     re.compile(r"(image: sentinel-audit-(?:api|dashboard):)[\w.\-]+"), r"\g<1>{v}"),
    (RAIZ / "deploy/helm/sentinel/Chart.yaml",
     re.compile(r'(appVersion: ")[^"]+(")'), r"\g<1>{v}\g<2>"),
    (RAIZ / "audit-api/src/main/resources/application.properties",
     re.compile(r"(quarkus\.smallrye-openapi\.info-version=)\S+"), r"\g<1>{v}"),
]


def versao() -> str:
    dados = json.loads((RAIZ / "audit-dashboard/package.json").read_text(encoding="utf-8"))
    return dados["version"]


def main(argv: list[str]) -> int:
    so_conferir = "--check" in argv
    v = versao()
    divergentes = []
    for arquivo, padrao, modelo in ALVOS:
        texto = arquivo.read_text(encoding="utf-8")
        novo = padrao.sub(modelo.replace("{v}", v), texto)
        if novo != texto:
            divergentes.append(arquivo.relative_to(RAIZ))
            if not so_conferir:
                arquivo.write_text(novo, encoding="utf-8")
    if divergentes:
        acao = "divergem de" if so_conferir else "atualizados para"
        print(f"{', '.join(map(str, divergentes))} {acao} {v}")
        return 1 if so_conferir else 0
    print(f"versões em dia ({v})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
