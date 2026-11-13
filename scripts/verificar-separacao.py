#!/usr/bin/env python3
"""Confere que o ciclo respeita a separação treino x validação (audit-rules/calibracao/conjuntos.json).

Uso: verificar-separacao.py [--base <ref-git>]     (padrão: origin/main; sem git ou sem a ref, pula só a checagem 3)
     verificar-separacao.py --atualizar-hash          (grava em estado.json o hash atual de conjuntos.json, depois de editar os conjuntos)

Checagens (código de saída 1 se alguma falhar):
  1. nenhum repositório está ao mesmo tempo em treino (repositorios_fixos + descobertos.txt) e em validação;
  2. gabaritos de treino (gabaritos/*.json) não são de repositório de validação, e todo gabarito de validação
     (gabaritos/validacao/*.json) cita `fonte` em cada falha (gabarito de validação não se monta lendo o código);
  3. linhas ADICIONADAS em audit-rules/packs/** desde a base não citam repositório de validação pelo nome
     (regra escrita olhando validação);
  4. docs/evolucao/estado.json existe e o hash de conjuntos.json que ele registra é o atual;
  5. o grupo reservado (conjuntos.json, `reservado`) não divide repositório com treino nem validação, e um repositório
     promovido (reservado/consultas.json, `promocoes`) nunca volta a reservado;
  6. gabarito de repositório reservado só mora em gabaritos/reservado/ e cita `fonte` em cada falha (e o contrário:
     nada que não seja reservado fica lá);
  7. nome `dono/repo` de repositório reservado só aparece nos arquivos permitidos (conjuntos, repositorios, gabaritos/reservado,
     reservado/consultas.json, docs/evolucao/medicao/, estado.json); medição do reservado não pode ter as chaves
     `achados`, `arquivo` nem `linha` (só contagens);
  8. reservado/consultas.json só cresce em relação à base git (linha editada ou removida é erro) e cada registro tem os campos exigidos.
"""
import hashlib, json, os, pathlib, re, subprocess, sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
CAL = RAIZ / "audit-rules" / "calibracao"


def _cal(raiz):
    return pathlib.Path(raiz) / "audit-rules" / "calibracao"


def carregar(raiz=RAIZ):
    """(treino, validação, reservado) como conjuntos de `dono/repo`."""
    cal = _cal(raiz)
    c = json.loads((cal / "conjuntos.json").read_text(encoding="utf-8"))
    desc = [l.strip() for l in (cal / "descobertos.txt").read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    treino = set(c["treino"]["repositorios_fixos"]) | set(desc)
    valid = set(c["validacao"]["repositorios_fixos"])
    reservado = set((c.get("reservado") or {}).get("repositorios_fixos") or [])
    return treino, valid, reservado


def checar_sobreposicao(treino, valid):
    return [f"{r} está em treino e em validação" for r in sorted(treino & valid)]


def checar_gabaritos(valid, raiz=RAIZ):
    cal = _cal(raiz)
    erros = []
    for g in sorted((cal / "gabaritos").glob("*.json")):
        repo = json.loads(g.read_text(encoding="utf-8")).get("repo", "")
        if repo in valid:
            erros.append(f"{g.relative_to(raiz)}: gabarito de TREINO de um repositório de validação ({repo}); mova para gabaritos/validacao/ só se vier de fonte pública")
    for g in sorted((cal / "gabaritos" / "validacao").glob("*.json")):
        d = json.loads(g.read_text(encoding="utf-8"))
        for i, f in enumerate(d.get("falhas") or []):
            if not str(f.get("fonte", "")).strip():
                erros.append(f"{g.relative_to(raiz)}: falha {i + 1} sem `fonte` (gabarito de validação precisa citar de onde veio)")
    return erros


def nomes_de(valid):
    # nome curto do repositório; ignora nomes genéricos que aparecem em qualquer regra
    curtos = {r.split("/")[-1].lower() for r in valid}
    return {n for n in curtos if n not in {"react-redux-realworld-example-app", "angular-realworld-example-app"}} | {"nodegoat", "dvja", "dvna"}


def checar_regras(valid, base):
    try:
        diff = subprocess.run(["git", "diff", "--unified=0", f"{base}...HEAD", "--", "audit-rules/packs"], cwd=RAIZ,
                              capture_output=True, text=True, check=True).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        print(f"aviso: sem git ou sem a ref {base}; checagem 3 pulada")
        return []
    nomes = nomes_de(valid)
    erros, arq = [], ""
    for ln in diff.splitlines():
        if ln.startswith("+++ b/"):
            arq = ln[6:]
        elif ln.startswith("+") and not ln.startswith("+++"):
            baixo = ln.lower()
            for n in nomes:
                if re.search(rf"(?<![a-z0-9]){re.escape(n)}(?![a-z0-9])", baixo):
                    erros.append(f"{arq}: linha adicionada cita repositório de validação '{n}': {ln[1:80].strip()}")
    return erros


def checar_estado(raiz=RAIZ):
    p = pathlib.Path(raiz) / "docs" / "evolucao" / "estado.json"
    if not p.exists():
        return ["docs/evolucao/estado.json não existe"]
    atual = hashlib.sha256((_cal(raiz) / "conjuntos.json").read_bytes()).hexdigest()
    reg = json.loads(p.read_text(encoding="utf-8")).get("conjuntosSha256")
    if reg != atual:
        return [f"estado.json registra conjuntosSha256={str(reg)[:12]}…, mas conjuntos.json é {atual[:12]}…: atualize o estado junto com os conjuntos"]
    return []


# --- grupo reservado (checagens 5 a 8) -------------------------------------------------------------------------

CHAVES_PROIBIDAS = ("achados", "arquivo", "linha")      # medição do reservado guarda só contagens (FR-006)
CAMPOS_CONSULTA = ("data", "versao_sentinel", "marco", "motivo", "medicao")
CAMPOS_PROMOCAO = ("data", "repo", "motivo", "versao_sentinel")
IGNORAR_DIRS = {".git", "node_modules", "target", "dist", "build", ".venv", "__pycache__", ".mvn"}


def _ler_registro(raiz):
    p = _cal(raiz) / "reservado" / "consultas.json"
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {"consultas": [], "promocoes": []}


def carregar_promovidos(raiz=RAIZ):
    return {p.get("repo") for p in _ler_registro(raiz).get("promocoes") or []}


def checar_exclusividade(treino, valid, reservado, promovidos=frozenset()):
    """Checagem 5: reservado não divide repositório com outro grupo; promovido nunca volta."""
    erros = [f"{r} está em reservado e em treino" for r in sorted(reservado & treino)]
    erros += [f"{r} está em reservado e em validação" for r in sorted(reservado & valid)]
    erros += [f"{r} foi promovido (reservado/consultas.json) e não pode voltar a reservado" for r in sorted(reservado & set(promovidos))]
    return erros


def checar_gabaritos_reservado(reservado, raiz=RAIZ):
    """Checagem 6: gabarito do reservado só em gabaritos/reservado/, com `fonte` em cada falha."""
    cal = _cal(raiz)
    erros = []
    for pasta in (cal / "gabaritos", cal / "gabaritos" / "validacao"):
        for g in sorted(pasta.glob("*.json")):
            repo = json.loads(g.read_text(encoding="utf-8")).get("repo", "")
            if repo in reservado:
                erros.append(f"{g.relative_to(raiz)}: gabarito de repositório reservado ({repo}) deve ficar em gabaritos/reservado/")
    for g in sorted((cal / "gabaritos" / "reservado").glob("*.json")):
        d = json.loads(g.read_text(encoding="utf-8"))
        if "repo" not in d:
            continue        # LEIAME.json com a regra de conduta
        if d["repo"] not in reservado:
            erros.append(f"{g.relative_to(raiz)}: gabarito de {d['repo']} está em gabaritos/reservado/, mas o repositório não é reservado")
        for i, f in enumerate(d.get("falhas") or []):
            if not str(f.get("fonte", "")).strip():
                erros.append(f"{g.relative_to(raiz)}: falha {i + 1} sem `fonte` (gabarito do reservado só de fonte pública)")
    return erros


def _permitido(rel):
    return (rel in ("audit-rules/calibracao/conjuntos.json", "audit-rules/calibracao/repositorios.json", "audit-rules/calibracao/reservado/consultas.json",
                    "docs/evolucao/estado.json")
            or rel.startswith("audit-rules/calibracao/gabaritos/reservado/") or rel.startswith("docs/evolucao/medicao/"))


def _chaves_proibidas(o, achadas):
    if isinstance(o, dict):
        for k, v in o.items():
            if k in CHAVES_PROIBIDAS:
                achadas.add(k)
            _chaves_proibidas(v, achadas)
    elif isinstance(o, list):
        for v in o:
            _chaves_proibidas(v, achadas)


def checar_vazamento_reservado(reservado, raiz=RAIZ):
    """Checagem 7: o nome de repositório reservado só aparece nos arquivos permitidos; medição do reservado só tem contagens.
    Limite conhecido: só o nome completo `dono/repo` é procurado, não o nome curto."""
    if not reservado:
        return []
    raiz = pathlib.Path(raiz)
    erros = []
    for dirpath, dirs, arquivos in os.walk(raiz):
        dirs[:] = sorted(d for d in dirs if d not in IGNORAR_DIRS)
        for nome in sorted(arquivos):
            caminho = pathlib.Path(dirpath) / nome
            rel = caminho.relative_to(raiz).as_posix()
            try:
                if caminho.stat().st_size > 2_000_000:
                    continue
                texto = caminho.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            if not _permitido(rel):
                baixo = texto.lower()
                for r in sorted(reservado):
                    if r.lower() in baixo:
                        erros.append(f"{rel}: cita o repositório reservado {r}; só os arquivos de calibração, de medição e o registro podem citar")
            elif rel.startswith("docs/evolucao/medicao/") and rel.endswith(".json"):
                try:
                    d = json.loads(texto)
                except ValueError:
                    continue
                if isinstance(d, dict) and d.get("conjunto") == "reservado":
                    achadas = set()
                    _chaves_proibidas(d, achadas)
                    for k in sorted(achadas):
                        erros.append(f"{rel}: medição do reservado tem a chave `{k}`; o reservado guarda só contagens")
    return erros


def checar_consultas(raiz, base_texto):
    """Checagem 8: o registro só cresce. `base_texto` é o conteúdo do arquivo na base git (None = sem base, pula a comparação)."""
    reg = _ler_registro(raiz)
    erros = []
    for i, c in enumerate(reg.get("consultas") or []):
        for campo in CAMPOS_CONSULTA:
            if not str(c.get(campo, "")).strip():
                erros.append(f"consultas.json: consulta {i + 1} sem `{campo}`")
    for i, p in enumerate(reg.get("promocoes") or []):
        for campo in CAMPOS_PROMOCAO:
            if not str(p.get(campo, "")).strip():
                erros.append(f"consultas.json: promoção {i + 1} sem `{campo}`")
    if base_texto:
        base = json.loads(base_texto)
        for lista, nome in (("consultas", "consulta"), ("promocoes", "promoção")):
            antes, agora = base.get(lista) or [], reg.get(lista) or []
            for i, linha in enumerate(antes):
                if i >= len(agora):
                    erros.append(f"consultas.json: {nome} {i + 1} removida (o registro só cresce)")
                elif agora[i] != linha:
                    erros.append(f"consultas.json: {nome} {i + 1} editada (o registro só cresce)")
    return erros


def base_do_registro(base):
    """Conteúdo de reservado/consultas.json na base git, ou None se não houver git, ref ou arquivo."""
    try:
        return subprocess.run(["git", "show", f"{base}:audit-rules/calibracao/reservado/consultas.json"], cwd=RAIZ,
                              capture_output=True, text=True, check=True).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def verificar(raiz=RAIZ, base="origin/main", base_consultas=None, com_regras=False):
    """Todas as checagens que não dependem de git (1, 2, 4 a 8); `com_regras` liga a 3."""
    treino, valid, reservado = carregar(raiz)
    erros = checar_sobreposicao(treino, valid) + checar_gabaritos(valid, raiz)
    if com_regras:
        erros += checar_regras(valid, base)
    return erros + checar_estado(raiz) + checar_exclusividade(treino, valid, reservado, carregar_promovidos(raiz)) \
        + checar_gabaritos_reservado(reservado, raiz) + checar_vazamento_reservado(reservado, raiz) + checar_consultas(raiz, base_consultas)


def atualizar_hash():
    p = RAIZ / "docs" / "evolucao" / "estado.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d["conjuntosSha256"] = hashlib.sha256((CAL / "conjuntos.json").read_bytes()).hexdigest()
    p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("estado.json atualizado:", d["conjuntosSha256"][:12])
    return 0


def main():
    if "--atualizar-hash" in sys.argv:
        return atualizar_hash()
    base = sys.argv[sys.argv.index("--base") + 1] if "--base" in sys.argv else "origin/main"
    treino, valid, reservado = carregar()
    base_reg = base_do_registro(base)
    if base_reg is None:
        print(f"aviso: sem a base {base} para reservado/consultas.json; comparação da checagem 8 pulada")
    erros = verificar(RAIZ, base, base_reg, com_regras=True)
    for e in erros:
        print("FALHA:", e)
    print(f"treino={len(treino)} validação={len(valid)} reservado={len(reservado)} problemas={len(erros)}")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
