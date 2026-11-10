#!/usr/bin/env python3
"""Confere se os números citados na documentação batem com o que está no código.

Conta regras, pacotes, perfis e ferramentas de mercado direto de audit-rules/packs e compara com o que README.md,
docs/IMPACTO.md e docs/referencia/pacotes-de-regras.md afirmam. Sai com 1 e lista as divergências (usado no CI),
para a narrativa não andar à frente (ou atrás) do código.

Uso: python3 scripts/conferir-numeros.py [raiz-do-repositório]
"""
import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent

REGRA = re.compile(r"^\s*- id:", re.MULTILINE)
FERRAMENTA = re.compile(r"^- id:", re.MULTILINE)


def regras_por_pacote(raiz: Path) -> dict[str, int]:
    """Número de regras (`- id:`) em rules/*.yml de cada pacote que tem pack.yml."""
    contagem = {}
    for pacote in sorted((raiz / "audit-rules/packs").iterdir()):
        if not (pacote / "pack.yml").is_file():
            continue
        contagem[pacote.name] = sum(
            len(REGRA.findall(f.read_text(encoding="utf-8")))
            for f in sorted((pacote / "rules").glob("*.y*ml"))
        ) if (pacote / "rules").is_dir() else 0
    return contagem


def ferramentas_de_mercado(raiz: Path) -> list[str]:
    """Ids de tools/*.yml do pacote market-tools."""
    ids = []
    for f in sorted((raiz / "audit-rules/packs/market-tools/tools").glob("*.y*ml")):
        ids += re.findall(r"^- id:\s*(\S+)", f.read_text(encoding="utf-8"), re.MULTILINE)
    return ids


def perfis(raiz: Path) -> int:
    return len(list((raiz / "audit-rules/packs/profiles").glob("*.y*ml")))


def divergencias(raiz: Path) -> list[str]:
    por_pacote = regras_por_pacote(raiz)
    total = sum(por_pacote.values())
    ferramentas = ferramentas_de_mercado(raiz)
    mercado = len([i for i in ferramentas if not i.startswith("joern")])
    esperado_resumo = (f"{len(por_pacote)} pacotes declarativos em `audit-rules/packs` ({total} regras próprias), "
                       f"{mercado} ferramentas de mercado via SARIF (mais o Joern, opcional) e {perfis(raiz)} perfis")
    problemas = []
    for doc in ("README.md", "docs/IMPACTO.md"):
        texto = (raiz / doc).read_text(encoding="utf-8")
        if esperado_resumo not in texto:
            problemas.append(f"{doc}: o resumo de regras e ferramentas deveria dizer: {esperado_resumo}")
        if re.search(r"Terraform \(Checkov\)", texto):
            problemas.append(f"{doc}: o Checkov roda só em Dockerfile, Kubernetes e GitHub Actions, não em Terraform")

    texto = (raiz / "docs/referencia/pacotes-de-regras.md").read_text(encoding="utf-8")
    for pacote, dito in re.findall(r"`([a-z0-9-]+)` \((\d+)\b", texto):
        if pacote in por_pacote and int(dito) != por_pacote[pacote]:
            problemas.append(f"docs/referencia/pacotes-de-regras.md: `{pacote}` diz {dito} regras, o código tem {por_pacote[pacote]}")
    return problemas


def main(argv: list[str]) -> int:
    raiz = Path(argv[1]).resolve() if len(argv) > 1 else RAIZ
    problemas = divergencias(raiz)
    for p in problemas:
        print("divergência:", p)
    if not problemas:
        print("números da documentação em dia")
    return 1 if problemas else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
