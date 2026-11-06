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
  4. docs/evolucao/estado.json existe e o hash de conjuntos.json que ele registra é o atual.
"""
import hashlib, json, pathlib, re, subprocess, sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
CAL = RAIZ / "audit-rules" / "calibracao"


def carregar():
    c = json.loads((CAL / "conjuntos.json").read_text(encoding="utf-8"))
    desc = [l.strip() for l in (CAL / "descobertos.txt").read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    treino = set(c["treino"]["repositorios_fixos"]) | set(desc)
    valid = set(c["validacao"]["repositorios_fixos"])
    return treino, valid


def checar_sobreposicao(treino, valid):
    return [f"{r} está em treino e em validação" for r in sorted(treino & valid)]


def checar_gabaritos(valid):
    erros = []
    for g in sorted((CAL / "gabaritos").glob("*.json")):
        repo = json.loads(g.read_text(encoding="utf-8")).get("repo", "")
        if repo in valid:
            erros.append(f"{g.relative_to(RAIZ)}: gabarito de TREINO de um repositório de validação ({repo}); mova para gabaritos/validacao/ só se vier de fonte pública")
    for g in sorted((CAL / "gabaritos" / "validacao").glob("*.json")):
        d = json.loads(g.read_text(encoding="utf-8"))
        for i, f in enumerate(d.get("falhas") or []):
            if not str(f.get("fonte", "")).strip():
                erros.append(f"{g.relative_to(RAIZ)}: falha {i + 1} sem `fonte` (gabarito de validação precisa citar de onde veio)")
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


def checar_estado():
    p = RAIZ / "docs" / "evolucao" / "estado.json"
    if not p.exists():
        return ["docs/evolucao/estado.json não existe"]
    atual = hashlib.sha256((CAL / "conjuntos.json").read_bytes()).hexdigest()
    reg = json.loads(p.read_text(encoding="utf-8")).get("conjuntosSha256")
    if reg != atual:
        return [f"estado.json registra conjuntosSha256={str(reg)[:12]}…, mas conjuntos.json é {atual[:12]}…: atualize o estado junto com os conjuntos"]
    return []


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
    treino, valid = carregar()
    erros = checar_sobreposicao(treino, valid) + checar_gabaritos(valid) + checar_regras(valid, base) + checar_estado()
    for e in erros:
        print("FALHA:", e)
    print(f"treino={len(treino)} validação={len(valid)} problemas={len(erros)}")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
