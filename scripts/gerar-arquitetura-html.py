#!/usr/bin/env python3
"""Gera docs/arquitetura/ARQUITETURA.html a partir de ARQUITETURA.md.

Mantém o cabeçalho (estilo) e o rodapé (script do sumário) do HTML atual e troca só o miolo.
Uso: python3 scripts/gerar-arquitetura-html.py   (precisa do pacote `markdown`)
"""
import html
import re
import sys
from pathlib import Path

import markdown
from markdown.extensions.toc import slugify as _slugify

RAIZ = Path(__file__).resolve().parent.parent
MD = RAIZ / "docs/arquitetura/ARQUITETURA.md"
HTML = RAIZ / "docs/arquitetura/ARQUITETURA.html"


def slug(valor, sep):
    return _slugify(valor, sep)


def main() -> int:
    texto = MD.read_text(encoding="utf-8")
    atual = HTML.read_text(encoding="utf-8")
    ini = atual.index('<div class="shell">')
    fim = atual.index("</main>")
    cabeca, rodape = atual[:ini], atual[fim:]

    # sumário do próprio HTML: só os títulos de nível 2
    corpo_md = texto.split("\n---\n", 1)[-1] if False else texto
    md = markdown.Markdown(extensions=["tables", "fenced_code", "toc"], extension_configs={"toc": {"slugify": slug}})
    # blocos mermaid viram <figure class="diagrama"><pre class="mermaid">
    marcadores = {}

    def guarda(m):
        chave = f"@@MERMAID{len(marcadores)}@@"
        marcadores[chave] = '<figure class="diagrama"><pre class="mermaid">' + html.escape(m.group(1).rstrip("\n"), quote=False) + "</pre></figure>"
        return "\n" + chave + "\n"

    texto = re.sub(r"```mermaid\n(.*?)```", guarda, texto, flags=re.S)
    corpo = md.convert(texto)
    for chave, bloco in marcadores.items():
        corpo = corpo.replace(f"<p>{chave}</p>", bloco).replace(chave, bloco)
    corpo = re.sub(r"<table>", '<div class="tabela"><table>', corpo)
    corpo = corpo.replace("</table>", "</table></div>")

    # sumário lateral
    itens = re.findall(r'<h2 id="([^"]+)">(.*?)</h2>', corpo)
    itens = [(i, t) for i, t in itens if re.match(r"\d+\.", re.sub(r"<[^>]+>", "", t))]
    nav = '<div class="shell">\n  <nav class="toc" aria-label="Sumário"><p class="t">Arquitetura</p>\n' + "\n".join(
        f'<a href="#{i}">{t}</a>' for i, t in itens
    ) + "\n  </nav>\n  <main>\n"
    corpo = re.sub(r'<h2 id="sumario">.*?(?=<h2 id="1-)', "", corpo, flags=re.S)
    HTML.write_text(cabeca + nav + corpo + "\n  " + rodape, encoding="utf-8")
    print(f"{HTML.relative_to(RAIZ)} gerado: {len(itens)} seções, {len(marcadores)} diagramas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
