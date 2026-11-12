#!/usr/bin/env python3
"""Régua do Sentinel (spec 003): mede com número, intervalo e honestidade. Só biblioteca padrão.

Uso: python3 -I scripts/medicao.py <relatorio|comparar|reservado|amostra|revisao|lacunas> ...
Contrato completo: specs/003-medicao-e-prova/contracts/cli.md. Códigos de saída: 0 ok, 1 falha de conteúdo,
2 uso incorreto, 3 sem medida/incomparável.

Princípio VII da constituição: este módulo só LÊ arquivos de medida (JSON) e nunca executa código auditado.
Por isso não dispara processos externos nem avalia texto como código (há teste que varre o código-fonte).
"""
import argparse, copy, datetime, json, os, random, re, sys

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
AMOSTRA_MINIMA = 20
REAMOSTRAS = 10000
GRUPOS = ("treino", "validacao", "reservado")
ORIGENS = ("proposital", "real")
# Escopo fixo de tecnologias (constituição, princípio VI).
PILHAS = ("JAVA", "SPRING", "QUARKUS", "ANGULAR", "REACT", "VUE", "TYPESCRIPT", "JAVASCRIPT", "HTML", "CSS",
          "DOCKER", "KUBERNETES", "SQL")
SHA40 = re.compile(r"^[0-9a-f]{40}$")
CHAVES_DE_ACHADO = {"achados", "arquivo", "linha", "regra", "regras"}  # nunca saem do relatório do reservado (FR-006)
NAO_IMPLEMENTADOS = ("comparar", "reservado", "amostra", "revisao")


# --- estatística -----------------------------------------------------------------------------------------------

def wilson(k, n, z=1.96):
    """Intervalo de Wilson de 95 % para k acertos em n (mesma conta de medir-volta.py)."""
    if n <= 0:
        return 0.0, 0.0
    p = k / n
    den = 1 + z * z / n
    centro = (p + z * z / (2 * n)) / den
    meia = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / den
    return max(0.0, centro - meia), min(1.0, centro + meia)


def amostra_pequena(n):
    return n < AMOSTRA_MINIMA


def bootstrap_pareado(antes, depois, semente, reamostras=REAMOSTRAS):
    """Ganho médio (depois - antes) por caso e intervalo de 95 % por bootstrap pareado, com semente fixa."""
    if len(antes) != len(depois):
        raise ValueError(f"listas de tamanhos diferentes: antes={len(antes)} depois={len(depois)}")
    if not antes:
        raise ValueError("listas vazias: não há o que comparar")
    dif = [d - a for a, d in zip(antes, depois)]
    n = len(dif)
    rng = random.Random(semente)
    medias = sorted(sum(dif[rng.randrange(n)] for _ in range(n)) / n for _ in range(reamostras))
    lo, hi = medias[int(0.025 * reamostras)], medias[min(reamostras - 1, int(0.975 * reamostras))]
    return {"ganho": round(sum(dif) / n, 6), "ic95": [round(lo, 6), round(hi, 6)], "n": n, "semente": semente}


def taxa(k, n):
    """Toda taxa sai com n, ic95 e o aviso de amostra pequena (SC-003)."""
    lo, hi = wilson(k, n)
    return {"valor": round(k / n, 3) if n else None, "ic95": [round(lo, 3), round(hi, 3)], "n": n,
            "amostra_pequena": amostra_pequena(n)}


# --- arquivos --------------------------------------------------------------------------------------------------

def gravar_json(caminho, obj):
    """JSON determinístico: chaves ordenadas, indentação 1, UTF-8, \\n no fim."""
    os.makedirs(os.path.dirname(os.path.abspath(caminho)), exist_ok=True)
    with open(caminho, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(obj, ensure_ascii=False, indent=1, sort_keys=True) + "\n")


def ler_json(caminho):
    with open(caminho, encoding="utf-8") as f:
        return json.load(f)


def carregar_conjuntos(caminho):
    """{grupo: [repositórios]} a partir de conjuntos.json (treino inclui a lista de descobertos, se houver)."""
    c = ler_json(caminho)
    saida = {g: [] for g in GRUPOS}
    for g in GRUPOS:
        bloco = c.get(g) or {}
        lista = list(bloco.get("repositorios_fixos") or [])
        desc = bloco.get("repositorios")
        if desc and os.path.exists(os.path.join(RAIZ, desc)):
            with open(os.path.join(RAIZ, desc), encoding="utf-8") as f:
                lista += [l.strip() for l in f if l.strip() and not l.startswith("#")]
        saida[g] = list(dict.fromkeys(lista))
    return saida


def validar_repositorios(repos_doc, conjuntos):
    """Regras 1 a 3 de contracts/arquivos.md. Devolve a lista de erros (vazia = válido)."""
    erros, vistos = [], {}
    for r in repos_doc.get("repositorios", []):
        nome = r.get("repo", "?")
        if nome in vistos:
            erros.append(f"{nome}: repetido em repositorios.json")
        vistos[nome] = r
        if r.get("grupo") not in GRUPOS:
            erros.append(f"{nome}: grupo inválido {r.get('grupo')!r} (use {', '.join(GRUPOS)})")
        if r.get("origem") not in ORIGENS:
            erros.append(f"{nome}: origem inválida {r.get('origem')!r} (use proposital ou real)")
        pilhas = r.get("pilhas") or []
        if not pilhas:
            erros.append(f"{nome}: pilhas vazia")
        for p in pilhas:
            if p not in PILHAS:
                erros.append(f"{nome}: pilha {p} fora do escopo fixo ({', '.join(PILHAS)})")
        c = r.get("commit")
        if c is not None and not (isinstance(c, str) and SHA40.match(c)):
            erros.append(f"{nome}: commit deve ter 40 caracteres hexadecimais (ou null se não reproduzível), veio {c!r}")
    donos = {}
    for g, lista in conjuntos.items():
        for repo in lista:
            donos.setdefault(repo, []).append(g)
    for repo, gs in sorted(donos.items()):
        if len(gs) > 1:
            erros.append(f"{repo}: está em mais de um grupo ({', '.join(gs)})")
        if repo not in vistos:
            erros.append(f"{repo}: sem registro em repositorios.json")
        elif vistos[repo].get("grupo") != gs[0]:
            erros.append(f"{repo}: grupo em repositorios.json ({vistos[repo].get('grupo')}) difere de conjuntos.json ({gs[0]})")
    for repo in sorted(set(vistos) - set(donos)):
        erros.append(f"{repo}: em repositorios.json mas fora de conjuntos.json")
    return erros


def nome_de_arquivo(repo):
    return repo.replace("/", "__") + ".json"


# --- medida e recall -------------------------------------------------------------------------------------------

def acertou(falha, achados):
    """Mesma regra de acerto de medir-volta.py: arquivo (sufixo), linha com tolerância e regra (sufixo)."""
    tol = falha.get("tolerancia", 3)
    return any(
        (not falha.get("arquivo") or a["arquivo"].endswith(falha["arquivo"]))
        and (falha.get("linha") is None or (a.get("linha") is not None and abs(a["linha"] - falha["linha"]) <= tol))
        and (not falha.get("regras") or any(a["regra"].endswith(r) for r in falha["regras"]))
        for a in achados)


def _soma(cat, nome, casos, tp):
    c = cat.setdefault(nome, {"casos": 0, "tp": 0})
    c["casos"] += casos
    c["tp"] += tp


def lacunas(rel):
    """Pilhas do escopo sem medida, com o que falta; prioridade 1 = mais repositórios `real` e nenhuma medida."""
    reais = {}
    for r in rel.get("repositorios", []):
        if r.get("origem") == "real":
            for p in r.get("pilhas", []):
                reais[p] = reais.get(p, 0) + 1
    faltam = [(p, v["motivo"]) for p, v in rel["por_pilha"].items() if v["estado"] == "sem-medida"]
    faltam.sort(key=lambda x: (-reais.get(x[0], 0), x[0]))
    return [{"pilha": p, "falta": motivo, "repositorios_reais": reais.get(p, 0), "prioridade": i + 1}
            for i, (p, motivo) in enumerate(faltam)]


def gerar_relatorio(conjunto, medidas, repositorios, gabaritos, data, marco=None, benchmark=None, id_relatorio=None):
    """Monta o relatório de um grupo. `medidas` e `gabaritos` são pastas; `repositorios` é o dict de repositorios.json."""
    if conjunto not in GRUPOS:
        raise ValueError(f"conjunto inválido: {conjunto}")
    if conjunto == "reservado" and not marco:
        raise ValueError("conjunto reservado exige --marco (FR-007)")
    repos = [r for r in repositorios["repositorios"] if r["grupo"] == conjunto]
    categorias, tempo, pendencias, medidos = {}, {}, [], []
    excluidos = {"codigo_gerado": 0, "terceiros": 0}
    com_gabarito = {}          # repo -> True quando a medida é completa e há gabarito com falhas
    versoes, ferramentas = set(), {}
    for r in sorted(repos, key=lambda x: x["repo"]):
        nome = r["repo"]
        caminho = os.path.join(medidas, nome_de_arquivo(nome))
        if not os.path.exists(caminho):
            pendencias.append({"repo": nome, "motivo": "sem medida: arquivo de medida não encontrado"})
            continue
        m = ler_json(caminho)
        if not m.get("completa"):
            pendencias.append({"repo": nome, "motivo": "medida incompleta: " + (m.get("motivo_incompleta") or "motivo não informado")})
            continue
        medidos.append(nome)
        if m.get("versao_sentinel"):
            versoes.add(m["versao_sentinel"])
        ferramentas.update({k: v for k, v in (m.get("versoes_ferramentas") or {}).items() if k not in ferramentas or ferramentas[k] is None})
        validos = []
        for a in m.get("achados", []):
            if a.get("excluido") in excluidos:
                excluidos[a["excluido"]] += 1
            else:
                validos.append(a)
        if m.get("duracao_s") is not None:
            tempo[nome] = {"duracao_s": m["duracao_s"], "arquivos": m.get("arquivos")}
        g = os.path.join(gabaritos, nome_de_arquivo(nome))
        falhas = ler_json(g).get("falhas", []) if os.path.exists(g) else []
        if not falhas:
            continue
        com_gabarito[nome] = True
        for f in falhas:
            _soma(categorias, f.get("cwe") or "sem-categoria", 1, 1 if acertou(f, validos) else 0)
    saida_cat = {}
    for nome, c in sorted(categorias.items()):
        saida_cat[nome] = {"casos": c["casos"], "tp": c["tp"], "taxa_acerto": taxa(c["tp"], c["casos"])}
    for nome, c in sorted(((benchmark or {}).get("categorias") or {}).items()):
        if c.get("vuln"):
            saida_cat["owasp:" + nome] = {"casos": c["vuln"] + c.get("seguro", 0), "tp": c["tp"], "fp": c["fp"],
                                          "taxa_acerto": taxa(c["tp"], c["vuln"]),
                                          "taxa_alarme_falso": taxa(c["fp"], c.get("seguro", 0))}
    por_pilha = {}
    for p in PILHAS:
        da_pilha = [r for r in repos if p in r["pilhas"]]
        medidos_p = [r["repo"] for r in da_pilha if r["repo"] in com_gabarito]
        if medidos_p:
            por_pilha[p] = {"estado": "medida", "repositorios": medidos_p}
        elif not da_pilha:
            por_pilha[p] = {"estado": "sem-medida", "motivo": "nenhum repositório do grupo com esta pilha"}
        elif all(r["repo"] not in medidos for r in da_pilha):
            por_pilha[p] = {"estado": "sem-medida", "motivo": "todas as medidas incompletas ou ausentes (" + ", ".join(r["repo"] for r in da_pilha) + ")"}
        else:
            por_pilha[p] = {"estado": "sem-medida", "motivo": "repositórios medidos, mas sem gabarito com falhas"}
    valores = [c["taxa_acerto"]["valor"] for c in saida_cat.values() if c["taxa_acerto"]["valor"] is not None]
    rel = {
        "id": id_relatorio or f"{data.replace('-', '')}-{conjunto}", "conjunto": conjunto, "data": data,
        "marco": marco, "proposta_sha256": None,
        "versao_sentinel": "; ".join(sorted(versoes)) if versoes else None,
        "versoes_ferramentas": ferramentas,
        "repositorios": [{"repo": r["repo"], "commit": r["commit"], "origem": r["origem"], "pilhas": r["pilhas"],
                          "estado": "medido" if r["repo"] in medidos else "pendente"} for r in sorted(repos, key=lambda x: x["repo"])],
        "por_pilha": por_pilha, "categorias": saida_cat, "excluidos": excluidos, "pendencias": pendencias,
        "resumo": {"media_taxa_acerto": round(sum(valores) / len(valores), 3) if valores else None, "categorias_medidas": len(valores)},
        "tempo": {"por_repositorio": tempo, "agregado_s": sum(t["duracao_s"] for t in tempo.values()),
                  "arquivos_total": sum(t["arquivos"] or 0 for t in tempo.values())},
    }
    rel["lacunas"] = lacunas(rel)
    sem_commit = [r["repo"] for r in repos if not r["commit"]]
    rel["verificado"] = [
        f"{len(medidos)} de {len(repos)} repositórios do grupo {conjunto} com medida completa.",
        "Intervalos de 95 % pelo método de Wilson; n e aviso de amostra pequena (n < 20) em toda taxa.",
        "Pilha sem gabarito ou sem medida completa aparece como sem-medida e fica fora das médias.",
    ]
    rel["nao_verificado"] = [
        "Os gabaritos não foram reconferidos nesta execução; valem como estão versionados.",
        "A duração é a medida no ambiente de origem; não é comparável entre máquinas.",
    ]
    if pendencias:
        rel["nao_verificado"].append(f"{len(pendencias)} repositório(s) pendente(s): " + ", ".join(p["repo"] for p in pendencias) + ".")
    if sem_commit:
        rel["nao_verificado"].append("Sem commit fixado (não reproduzível): " + ", ".join(sem_commit) + ".")
    if conjunto == "reservado":
        proibidas = CHAVES_DE_ACHADO & _chaves(rel)
        if proibidas:   # FR-006: achados individuais do reservado nunca saem
            raise ValueError("relatório do reservado não pode conter achados individuais: " + ", ".join(sorted(proibidas)))
    return rel


def _chaves(o, acc=None):
    acc = set() if acc is None else acc
    if isinstance(o, dict):
        for k, v in o.items():
            acc.add(k); _chaves(v, acc)
    elif isinstance(o, list):
        for v in o:
            _chaves(v, acc)
    return acc


def validar_relatorio(rel):
    """Regra 8: toda taxa precisa de n e ic95. Devolve a lista de erros."""
    erros = []
    for nome, c in rel.get("categorias", {}).items():
        for t in ("taxa_acerto", "taxa_alarme_falso"):
            if t in c:
                for campo in ("n", "ic95", "amostra_pequena"):
                    if campo not in c[t]:
                        erros.append(f"categoria {nome}: {t} sem {campo}")
    return erros


def registrar_correcao(antigo, novo, motivo, data):
    """Devolve `novo` com uma entrada a mais em `historico` (antes/depois); nada é sobrescrito sem registro (FR-015)."""
    if not (motivo or "").strip():
        raise ValueError("correção exige motivo")
    sem = lambda d: {k: copy.deepcopy(v) for k, v in d.items() if k != "historico"}
    saida = sem(novo)
    saida["historico"] = list(antigo.get("historico") or []) + [
        {"data": data, "motivo": motivo.strip(), "antes": sem(antigo), "depois": sem(novo)}]
    return saida


def _pct(t):
    if t["valor"] is None:
        return "sem casos"
    aviso = " (amostra pequena)" if t["amostra_pequena"] else ""
    return f"{100 * t['valor']:.1f} % [IC95 {100 * t['ic95'][0]:.1f} a {100 * t['ic95'][1]:.1f}], n={t['n']}{aviso}"


def relatorio_md(rel):
    L = [f"# Medição {rel['id']}", "",
         f"- Grupo: {rel['conjunto']}" + (f" (marco: {rel['marco']})" if rel.get("marco") else ""),
         f"- Data: {rel['data']}", f"- Versão do Sentinel: {rel['versao_sentinel'] or 'não informada'}",
         "- Ferramentas: " + (", ".join(f"{k} {v or 'ausente'}" for k, v in sorted(rel["versoes_ferramentas"].items())) or "não informadas"), "",
         "## Repositórios", "", "| Repositório | Commit | Origem | Estado |", "|---|---|---|---|"]
    for r in rel["repositorios"]:
        L.append(f"| {r['repo']} | {r['commit'] or 'não reproduzível'} | {r['origem']} | {r['estado']} |")
    L += ["", "## Taxas por categoria", "", "| Categoria | Acerto | Alarme falso |", "|---|---|---|"]
    for nome, c in rel["categorias"].items():
        L.append(f"| {nome} | {_pct(c['taxa_acerto'])} | {_pct(c['taxa_alarme_falso']) if 'taxa_alarme_falso' in c else 'não medido'} |")
    media = rel["resumo"]["media_taxa_acerto"]
    L += ["", f"Média simples da taxa de acerto: {('%.1f %%' % (100 * media)) if media is not None else 'sem medida'} "
              f"({rel['resumo']['categorias_medidas']} categorias; pilhas sem medida ficam fora da média).", "",
          "## Pilhas", "", "| Pilha | Estado | Observação |", "|---|---|---|"]
    for p, v in rel["por_pilha"].items():
        L.append(f"| {p} | {v['estado']} | {v.get('motivo') or ', '.join(v.get('repositorios', []))} |")
    if rel["lacunas"]:
        L += ["", "## Lacunas (o que falta medir)", "", "| Prioridade | Pilha | Falta |", "|---|---|---|"]
        L += [f"| {l['prioridade']} | {l['pilha']} | {l['falta']} |" for l in rel["lacunas"]]
    L += ["", "## Tempo e tamanho", "",
          f"Duração somada: {rel['tempo']['agregado_s']} s; arquivos de código: {rel['tempo']['arquivos_total']}.",
          f"Excluídos da conta: {rel['excluidos']['codigo_gerado']} em código gerado, {rel['excluidos']['terceiros']} em código de terceiros.",
          "", "## Verificado", ""] + [f"- {x}" for x in rel["verificado"]] + ["", "## NÃO verificado", ""] + [f"- {x}" for x in rel["nao_verificado"]]
    return "\n".join(L) + "\n"


# --- linha de comando ------------------------------------------------------------------------------------------

def _cmd_relatorio(a):
    if a.conjunto == "reservado":
        print("O grupo reservado só pode ser medido quando o registro de consultas existir (fatia C da spec 003).", file=sys.stderr)
        return 2
    cal = os.path.join(RAIZ, "audit-rules", "calibracao")
    repos = ler_json(a.repositorios or os.path.join(cal, "repositorios.json"))
    erros = validar_repositorios(repos, carregar_conjuntos(os.path.join(cal, "conjuntos.json")) if not a.repositorios else
                                 {g: [r["repo"] for r in repos["repositorios"] if r["grupo"] == g] for g in GRUPOS})
    if erros:
        print("repositorios.json inválido:\n  " + "\n  ".join(erros), file=sys.stderr)
        return 1
    gab = a.gabaritos or os.path.join(cal, "gabaritos", {"treino": "", "validacao": "validacao"}[a.conjunto])
    bench = ler_json(a.benchmark) if a.benchmark else None
    rel = gerar_relatorio(a.conjunto, a.medidas, repos, gab, a.data or datetime.date.today().isoformat(), marco=a.marco,
                          benchmark=bench, id_relatorio=a.id)
    erros = validar_relatorio(rel)
    if erros:
        print("relatório inválido:\n  " + "\n  ".join(erros), file=sys.stderr)
        return 1
    saida = a.saida or os.path.join(RAIZ, "docs", "evolucao", "medicao")
    gravar_json(os.path.join(saida, rel["id"] + ".json"), rel)
    with open(os.path.join(saida, rel["id"] + ".md"), "w", encoding="utf-8", newline="\n") as f:
        f.write(relatorio_md(rel))
    print(f"Relatório {rel['id']}: {rel['resumo']['categorias_medidas']} categorias medidas, {len(rel['pendencias'])} pendência(s), {len(rel['lacunas'])} lacuna(s).")
    return 0


def _cmd_lacunas(a):
    rel = ler_json(a.medicao)
    lac = rel.get("lacunas") if "lacunas" in rel else lacunas(rel)
    if not lac:
        print("Nenhuma lacuna: todas as pilhas do escopo têm medida.")
        return 0
    for l in lac:
        print(f"{l['prioridade']:>2}. {l['pilha']}: {l['falta']}")
    return 0


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv and argv[0] in NAO_IMPLEMENTADOS:
        print(f"Subcomando '{argv[0]}' ainda não implementado nesta fatia (ver specs/003-medicao-e-prova/tasks.md).", file=sys.stderr)
        return 2
    ap = argparse.ArgumentParser(prog="medicao.py", description=__doc__.splitlines()[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("relatorio")
    r.add_argument("--conjunto", required=True, choices=GRUPOS)
    r.add_argument("--medidas", required=True)
    r.add_argument("--marco"); r.add_argument("--saida"); r.add_argument("--benchmark")
    r.add_argument("--repositorios", help="(testes) repositorios.json alternativo")
    r.add_argument("--gabaritos", help="(testes) pasta de gabaritos alternativa")
    r.add_argument("--data", help="AAAA-MM-DD; padrão hoje"); r.add_argument("--id")
    l = sub.add_parser("lacunas")
    l.add_argument("--medicao", required=True)
    a = ap.parse_args(argv)
    return {"relatorio": _cmd_relatorio, "lacunas": _cmd_lacunas}[a.cmd](a)


if __name__ == "__main__":
    sys.exit(main())
