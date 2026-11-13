#!/usr/bin/env python3
"""Testes das checagens 5 a 8 de scripts/verificar-separacao.py (grupo reservado, spec 003, fatia C).
Rodar: python3 -m unittest scripts/test_verificar_separacao.py. Cada teste monta uma árvore de repositório pequena num diretório temporário."""
import hashlib, importlib.util, json, os, tempfile, unittest

AQUI = os.path.dirname(os.path.abspath(__file__))


def carregar(nome, arquivo):
    spec = importlib.util.spec_from_file_location(nome, os.path.join(AQUI, arquivo))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


V = carregar("verificar_separacao", "verificar-separacao.py")
RES = "res/alvo"


def escrever(raiz, rel, conteudo):
    caminho = os.path.join(raiz, rel)
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8") as f:
        f.write(conteudo if isinstance(conteudo, str) else json.dumps(conteudo, indent=1, sort_keys=True) + "\n")


def ler(raiz, rel):
    with open(os.path.join(raiz, rel), encoding="utf-8") as f:
        return f.read()


def falha(fonte="https://exemplo.invalid/lista"):
    f = {"arquivo": "A.java", "linha": 3, "regras": ["x"], "cwe": "CWE-89"}
    if fonte is not None:
        f["fonte"] = fonte
    return f


def montar(raiz, reservado=(RES,), treino=("t/um",), valid=("v/dois",), consultas=None, promocoes=None):
    cal = "audit-rules/calibracao/"
    escrever(raiz, cal + "conjuntos.json", {"treino": {"repositorios_fixos": list(treino)}, "validacao": {"repositorios_fixos": list(valid)},
             "reservado": {"repositorios_fixos": list(reservado), "regra": "ninguém lê", "marcos": ["marco-0"]}})
    escrever(raiz, cal + "descobertos.txt", "")
    escrever(raiz, cal + "reservado/consultas.json", {"consultas": consultas or [], "promocoes": promocoes or []})
    for r in reservado:
        escrever(raiz, cal + "gabaritos/reservado/" + r.replace("/", "__") + ".json", {"repo": r, "falhas": [falha()]})
    with open(os.path.join(raiz, cal + "conjuntos.json"), "rb") as f:
        escrever(raiz, "docs/evolucao/estado.json", {"conjuntosSha256": hashlib.sha256(f.read()).hexdigest()})


class ReservadoNaSeparacao(unittest.TestCase):
    def setUp(self):
        self._t = tempfile.TemporaryDirectory(); self.addCleanup(self._t.cleanup)
        self.raiz = self._t.name

    def todas(self, base=None):
        return V.verificar(self.raiz, base_consultas=base)

    def test_arvore_correta_nao_tem_erro(self):
        montar(self.raiz)
        self.assertEqual(self.todas(base=ler(self.raiz, "audit-rules/calibracao/reservado/consultas.json")), [])

    # checagem 5
    def test_reservado_tambem_em_treino_ou_validacao_e_erro(self):
        for onde in ("treino", "valid"):
            montar(self.raiz, **{onde: (RES,)} if onde == "valid" else {"treino": (RES,)})
            erros = V.checar_exclusividade(*V.carregar(self.raiz))
            self.assertTrue(any(RES in e and "reservado" in e for e in erros), (onde, erros))

    def test_promovido_nao_volta_a_reservado(self):
        montar(self.raiz, promocoes=[{"data": "2026-11-13", "repo": RES, "motivo": "teste", "versao_sentinel": "1"}])
        erros = V.checar_exclusividade(*V.carregar(self.raiz), promovidos=V.carregar_promovidos(self.raiz))
        self.assertTrue(any(RES in e and "promovido" in e for e in erros), erros)

    # checagem 6
    def test_gabarito_do_reservado_fora_da_pasta_propria_e_erro(self):
        montar(self.raiz)
        escrever(self.raiz, "audit-rules/calibracao/gabaritos/res__alvo.json", {"repo": RES, "falhas": [falha()]})
        erros = V.checar_gabaritos_reservado(V.carregar(self.raiz)[2], self.raiz)
        self.assertTrue(any("gabaritos/reservado" in e and RES in e for e in erros), erros)

    def test_gabarito_do_reservado_sem_fonte_e_erro(self):
        montar(self.raiz)
        escrever(self.raiz, "audit-rules/calibracao/gabaritos/reservado/res__alvo.json", {"repo": RES, "falhas": [falha(), falha(fonte=None)]})
        erros = V.checar_gabaritos_reservado(V.carregar(self.raiz)[2], self.raiz)
        self.assertTrue(any("falha 2" in e and "fonte" in e for e in erros), erros)

    def test_leiame_da_pasta_do_reservado_e_ignorado(self):
        montar(self.raiz)
        escrever(self.raiz, "audit-rules/calibracao/gabaritos/reservado/LEIAME.json", {"regra": "ninguém lê o código"})
        self.assertEqual(V.checar_gabaritos_reservado(V.carregar(self.raiz)[2], self.raiz), [])

    # checagem 7
    def test_nome_em_arquivo_nao_permitido_e_erro(self):
        montar(self.raiz)
        escrever(self.raiz, "docs/notas.md", f"olhei {RES} ontem\n")
        erros = V.checar_vazamento_reservado(V.carregar(self.raiz)[2], self.raiz)
        self.assertTrue(any("docs/notas.md" in e and RES in e for e in erros), erros)

    def test_nome_nos_arquivos_permitidos_e_aceito(self):
        montar(self.raiz)
        escrever(self.raiz, "audit-rules/calibracao/repositorios.json", {"repositorios": [{"repo": RES}]})
        escrever(self.raiz, "docs/evolucao/medicao/20261113-marco-0.json", {"conjunto": "reservado", "categorias": {}, "repositorios": [{"repo": RES}]})
        self.assertEqual(V.checar_vazamento_reservado(V.carregar(self.raiz)[2], self.raiz), [])

    def test_sem_reservado_nao_varre_nada(self):
        montar(self.raiz, reservado=())
        escrever(self.raiz, "docs/x.md", "qualquer coisa\n")
        self.assertEqual(V.checar_vazamento_reservado(set(), self.raiz), [])

    def test_medicao_do_reservado_com_achados_arquivo_ou_linha_e_erro(self):
        montar(self.raiz)
        for chave in ("achados", "arquivo", "linha"):
            escrever(self.raiz, "docs/evolucao/medicao/m.json", {"conjunto": "reservado", "x": [{chave: 1}]})
            erros = V.checar_vazamento_reservado(V.carregar(self.raiz)[2], self.raiz)
            self.assertTrue(any("m.json" in e and chave in e for e in erros), (chave, erros))

    def test_medicao_de_treino_pode_ter_essas_chaves(self):
        montar(self.raiz)
        escrever(self.raiz, "docs/evolucao/medicao/t.json", {"conjunto": "treino", "x": [{"arquivo": "a", "linha": 1}]})
        self.assertEqual(V.checar_vazamento_reservado(V.carregar(self.raiz)[2], self.raiz), [])

    # checagem 8
    def test_registro_so_cresce(self):
        a = {"data": "2026-11-13", "versao_sentinel": "1", "marco": "marco-0", "motivo": "base", "medicao": "m1"}
        b = dict(a, medicao="m2")
        base = json.dumps({"consultas": [a, b], "promocoes": []})
        def atual(cons, prom=()):
            escrever(self.raiz, "audit-rules/calibracao/reservado/consultas.json", {"consultas": cons, "promocoes": list(prom)})
            return V.checar_consultas(self.raiz, base)
        montar(self.raiz)
        self.assertEqual(atual([a, b, dict(a, medicao="m3")]), [])                       # acrescentada: aceita
        self.assertTrue(any("editada" in e for e in atual([a, dict(b, motivo="outro")])))  # editada
        self.assertTrue(any("removida" in e for e in atual([a])))                          # removida

    def test_promocao_removida_e_erro(self):
        p = {"data": "2026-11-13", "repo": RES, "motivo": "x", "versao_sentinel": "1"}
        base = json.dumps({"consultas": [], "promocoes": [p]})
        montar(self.raiz)
        self.assertTrue(any("removida" in e for e in V.checar_consultas(self.raiz, base)))

    def test_consulta_sem_campo_obrigatorio_e_erro(self):
        montar(self.raiz, consultas=[{"data": "2026-11-13", "marco": "m"}])
        erros = V.checar_consultas(self.raiz, None)
        self.assertTrue(any("versao_sentinel" in e for e in erros), erros)

    def test_sem_base_pula_so_a_comparacao(self):
        montar(self.raiz)
        self.assertEqual(V.checar_consultas(self.raiz, None), [])

    def test_estado_desatualizado_continua_sendo_erro(self):
        montar(self.raiz)
        escrever(self.raiz, "audit-rules/calibracao/conjuntos.json", ler(self.raiz, "audit-rules/calibracao/conjuntos.json") + " ")
        self.assertTrue(V.checar_estado(self.raiz))


if __name__ == "__main__":
    unittest.main()
