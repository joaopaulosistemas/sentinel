#!/usr/bin/env python3
"""Testes de scripts/medicao.py (spec 003, fatia A). Rodar: python3 -I -m unittest scripts/test_medicao.py
Só biblioteca padrão. O módulo é carregado por caminho porque `python3 -I` não põe a pasta do script no sys.path."""
import copy, importlib.util, io, json, os, re, tempfile, unittest
from contextlib import redirect_stdout, redirect_stderr

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
FIX = os.path.join(AQUI, "fixtures", "medicao")


def carregar(nome, arquivo):
    spec = importlib.util.spec_from_file_location(nome, os.path.join(AQUI, arquivo))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


M = carregar("medicao", "medicao.py")
V = carregar("medir_volta", "medir-volta.py")


def ler(caminho, modo="r"):
    with open(caminho, modo, **({} if "b" in modo else {"encoding": "utf-8"})) as f:
        return f.read()


def lj(*partes):
    return json.loads(ler(os.path.join(FIX, *partes)))


def relatorio(conjunto="treino", **kw):
    return M.gerar_relatorio(conjunto, os.path.join(FIX, "medidas"), lj("repositorios.json"), os.path.join(FIX, "gabaritos"),
                             data="2026-11-12", **kw)


def chaves(o, acc=None):
    acc = set() if acc is None else acc
    if isinstance(o, dict):
        for k, v in o.items():
            acc.add(k); chaves(v, acc)
    elif isinstance(o, list):
        for v in o:
            chaves(v, acc)
    return acc


class Estatistica(unittest.TestCase):
    def test_wilson_igual_ao_medir_volta(self):
        for k, n in [(0, 10), (3, 10), (10, 10), (66, 120), (1, 1), (7, 50)]:
            self.assertEqual(M.wilson(k, n), V.wilson(k, n), (k, n))

    def test_wilson_n_zero_nao_divide(self):
        self.assertEqual(M.wilson(0, 0), (0.0, 0.0))

    def test_amostra_pequena_abaixo_de_20(self):
        self.assertTrue(M.amostra_pequena(19))
        self.assertFalse(M.amostra_pequena(20))
        self.assertEqual(M.AMOSTRA_MINIMA, 20)

    def test_bootstrap_mesma_semente_mesmo_intervalo(self):
        a, d = lj("antes.json")["resultados"]["sqli"], lj("depois-melhora.json")["resultados"]["sqli"]
        r1, r2 = M.bootstrap_pareado(a, d, 7), M.bootstrap_pareado(a, d, 7)
        self.assertEqual(json.dumps(r1, sort_keys=True), json.dumps(r2, sort_keys=True))

    def test_bootstrap_melhora_clara_limite_inferior_positivo(self):
        r = M.bootstrap_pareado(lj("antes.json")["resultados"]["sqli"], lj("depois-melhora.json")["resultados"]["sqli"], 7)
        self.assertGreater(r["ic95"][0], 0)

    def test_bootstrap_sem_efeito_inclui_zero(self):
        r = M.bootstrap_pareado(lj("antes.json")["resultados"]["sqli"], lj("depois-igual.json")["resultados"]["sqli"], 7)
        self.assertLessEqual(r["ic95"][0], 0)
        self.assertGreaterEqual(r["ic95"][1], 0)

    def test_bootstrap_tamanhos_diferentes_e_erro(self):
        with self.assertRaises(ValueError):
            M.bootstrap_pareado([0, 1], [1], 1)


class Seguranca(unittest.TestCase):
    def test_modulo_nao_executa_codigo_auditado(self):
        # FR-017 / princípio VII: nada de subprocess, os.system, eval ou exec.
        fonte = ler(os.path.join(AQUI, "medicao.py"))
        sem_comentarios = "\n".join(l.split("#")[0] for l in fonte.splitlines())
        for proibido in (r"\bsubprocess\b", r"os\.system", r"\beval\s*\(", r"\bexec\s*\(", r"os\.popen"):
            self.assertIsNone(re.search(proibido, sem_comentarios), proibido)


class Validacao(unittest.TestCase):
    def setUp(self):
        self.repos = lj("repositorios.json")
        self.conj = M.carregar_conjuntos(os.path.join(FIX, "conjuntos.json"))

    def test_fixture_valida(self):
        self.assertEqual(M.validar_repositorios(self.repos, self.conj), [])

    def test_repositorio_em_dois_grupos(self):
        c = copy.deepcopy(self.conj); c["validacao"].append("fix/java-app")
        erros = M.validar_repositorios(self.repos, c)
        self.assertTrue(any("fix/java-app" in e and "mais de um grupo" in e for e in erros), erros)

    def test_repositorio_sem_registro(self):
        r = copy.deepcopy(self.repos); r["repositorios"].pop(0)
        erros = M.validar_repositorios(r, self.conj)
        self.assertTrue(any("fix/java-app" in e and "sem registro" in e for e in erros), erros)

    def test_grupo_diferente_do_conjuntos(self):
        r = copy.deepcopy(self.repos); r["repositorios"][0]["grupo"] = "validacao"
        erros = M.validar_repositorios(r, self.conj)
        self.assertTrue(any("fix/java-app" in e and "grupo" in e for e in erros), erros)

    def test_pilha_fora_do_escopo(self):
        r = copy.deepcopy(self.repos); r["repositorios"][0]["pilhas"] = ["GOLANG"]
        erros = M.validar_repositorios(r, self.conj)
        self.assertTrue(any("GOLANG" in e for e in erros), erros)

    def test_pilhas_vazia(self):
        r = copy.deepcopy(self.repos); r["repositorios"][0]["pilhas"] = []
        self.assertTrue(M.validar_repositorios(r, self.conj))

    def test_commit_com_tamanho_errado(self):
        r = copy.deepcopy(self.repos); r["repositorios"][0]["commit"] = "abc123"
        erros = M.validar_repositorios(r, self.conj)
        self.assertTrue(any("commit" in e for e in erros), erros)

    def test_commit_nulo_e_permitido(self):
        r = copy.deepcopy(self.repos); r["repositorios"][0]["commit"] = None
        self.assertEqual(M.validar_repositorios(r, self.conj), [])

    def test_arquivo_real_do_projeto_e_valido(self):
        conj = M.carregar_conjuntos(os.path.join(RAIZ, "audit-rules", "calibracao", "conjuntos.json"))
        repos = json.loads(ler(os.path.join(RAIZ, "audit-rules", "calibracao", "repositorios.json")))
        self.assertEqual(M.validar_repositorios(repos, conj), [])

    def test_gravar_json_deterministico(self):
        with tempfile.TemporaryDirectory() as d:
            p = os.path.join(d, "x.json")
            M.gravar_json(p, {"b": 1, "a": [1, 2]})
            t = ler(p)
        self.assertEqual(t, '{\n "a": [\n  1,\n  2\n ],\n "b": 1\n}\n')


class Relatorio(unittest.TestCase):
    def test_relatorio_repetido_tem_diff_vazio(self):
        with tempfile.TemporaryDirectory() as d:
            saidas = []
            for i in (1, 2):
                s = os.path.join(d, str(i)); os.makedirs(s)
                args = ["relatorio", "--conjunto", "treino", "--medidas", os.path.join(FIX, "medidas"), "--saida", s,
                        "--repositorios", os.path.join(FIX, "repositorios.json"), "--gabaritos", os.path.join(FIX, "gabaritos"),
                        "--data", "2026-11-12", "--id", "teste"]
                with redirect_stdout(io.StringIO()):
                    self.assertEqual(M.main(args), 0)
                saidas.append({f: ler(os.path.join(s, f), "rb") for f in sorted(os.listdir(s))})
        self.assertEqual(sorted(saidas[0]), ["teste.json", "teste.md"])
        self.assertEqual(saidas[0], saidas[1])

    def test_toda_taxa_tem_n_e_ic95(self):
        r = relatorio()
        self.assertTrue(r["categorias"])
        for nome, c in r["categorias"].items():
            for t in ("taxa_acerto",):
                self.assertIn("n", c[t]); self.assertIn("ic95", c[t]); self.assertIn("amostra_pequena", c[t], nome)
                self.assertEqual(c[t]["amostra_pequena"], c[t]["n"] < 20)

    def test_taxa_sem_ic95_invalida_o_relatorio(self):
        r = relatorio()
        r["categorias"]["CWE-89"]["taxa_acerto"].pop("ic95")
        self.assertTrue(M.validar_relatorio(r))

    def test_recall_por_gabarito(self):
        c = relatorio()["categorias"]
        self.assertEqual((c["CWE-89"]["casos"], c["CWE-89"]["tp"]), (2, 2))   # java-app + js-app; o gabarito do incompleto fica fora
        self.assertEqual((c["CWE-79"]["casos"], c["CWE-79"]["tp"]), (3, 2))   # C.java perdida

    def test_pilha_sem_gabarito_fica_sem_medida_e_fora_das_medias(self):
        r = relatorio()
        k = r["por_pilha"]["KUBERNETES"]
        self.assertEqual(k["estado"], "sem-medida"); self.assertTrue(k["motivo"])
        medidas = [c["taxa_acerto"]["valor"] for c in r["categorias"].values()]
        self.assertAlmostEqual(r["resumo"]["media_taxa_acerto"], sum(medidas) / len(medidas), places=3)
        self.assertEqual(sorted(r["por_pilha"]), sorted(M.PILHAS))

    def test_medida_incompleta_nao_vira_zero(self):
        r = relatorio()
        self.assertEqual(r["por_pilha"]["SPRING"]["estado"], "sem-medida")
        self.assertIn("incompleta", r["por_pilha"]["SPRING"]["motivo"])
        pend = [p for p in r["pendencias"] if p["repo"] == "fix/incompleto"]
        self.assertEqual(len(pend), 1)
        self.assertIn("tempo", pend[0]["motivo"])
        self.assertNotIn("fix/incompleto", r["tempo"]["por_repositorio"])

    def test_md_tem_verificado_e_nao_verificado_no_fim(self):
        r = relatorio(); md = M.relatorio_md(r)
        self.assertIn("## Verificado", md); self.assertIn("## NÃO verificado", md)
        self.assertLess(md.index("## Verificado"), md.index("## NÃO verificado"))
        self.assertTrue(md.rstrip().splitlines()[-1].startswith("- "))
        self.assertIn("0.0.0-teste", md); self.assertIn("2026-11-12", md); self.assertIn("a" * 40, md)
        self.assertIn("opengrep", md)

    def test_codigo_gerado_e_terceiros_excluidos(self):
        r = relatorio()
        self.assertEqual(r["excluidos"], {"codigo_gerado": 1, "terceiros": 1})

    def test_reservado_sem_achados_individuais(self):
        # grupo "reservado" fictício: reaproveita as fixtures reclassificando o grupo
        repos = lj("repositorios.json")
        for x in repos["repositorios"]:
            x["grupo"] = "reservado"
        r = M.gerar_relatorio("reservado", os.path.join(FIX, "medidas"), repos, os.path.join(FIX, "gabaritos"),
                              data="2026-11-12", marco="marco-teste")
        proibidas = {"achados", "arquivo", "linha", "regra", "regras"} & chaves(r)
        self.assertFalse(proibidas, proibidas)
        self.assertTrue(r["categorias"])
        self.assertNotIn("A.java", json.dumps(r))

    def test_reservado_exige_marco(self):
        with self.assertRaises(ValueError):
            M.gerar_relatorio("reservado", os.path.join(FIX, "medidas"), lj("repositorios.json"), os.path.join(FIX, "gabaritos"), data="2026-11-12")

    def test_benchmark_owasp_vira_categorias(self):
        bench = {"media": 0.4, "categorias": {"sqli": {"vuln": 100, "seguro": 50, "tp": 80, "fp": 5, "tpr": 0.8, "fpr": 0.1}}}
        c = relatorio(benchmark=bench)["categorias"]["owasp:sqli"]
        self.assertEqual((c["tp"], c["fp"]), (80, 5))
        self.assertEqual(c["taxa_acerto"]["n"], 100); self.assertEqual(c["taxa_alarme_falso"]["n"], 50)
        self.assertEqual(c["taxa_alarme_falso"]["valor"], 0.1)
        self.assertLess(c["taxa_acerto"]["ic95"][0], 0.8)

    def test_tempo_e_tamanho_com_agregados(self):
        t = relatorio()["tempo"]
        self.assertEqual(t["por_repositorio"]["fix/java-app"], {"duracao_s": 40, "arquivos": 10})
        self.assertEqual(t["agregado_s"], 60); self.assertEqual(t["arquivos_total"], 14)

    def test_correcao_de_gabarito_acrescenta_historico(self):
        g = lj("gabaritos", "fix__java-app.json")
        novo = copy.deepcopy(g); novo["falhas"][0]["linha"] = 12
        out = M.registrar_correcao(g, novo, "linha conferida na fonte", "2026-11-12")
        self.assertEqual(len(out["historico"]), 1)
        h = out["historico"][0]
        self.assertEqual((h["data"], h["motivo"]), ("2026-11-12", "linha conferida na fonte"))
        self.assertEqual(h["antes"]["falhas"][0]["linha"], 11); self.assertEqual(h["depois"]["falhas"][0]["linha"], 12)
        out2 = M.registrar_correcao(out, {**out, "falhas": out["falhas"][:2]}, "falha duplicada", "2026-11-13")
        self.assertEqual(len(out2["historico"]), 2)
        self.assertEqual(out2["historico"][0], h)   # a anterior continua intacta

    def test_correcao_sem_motivo_e_recusada(self):
        g = lj("gabaritos", "fix__java-app.json")
        with self.assertRaises(ValueError):
            M.registrar_correcao(g, copy.deepcopy(g), "", "2026-11-12")


class Lacunas(unittest.TestCase):
    def test_lista_pilhas_sem_medida_com_falta_e_prioridade(self):
        r = relatorio()
        lac = M.lacunas(r)
        nomes = [l["pilha"] for l in lac]
        self.assertNotIn("JAVA", nomes); self.assertNotIn("JAVASCRIPT", nomes)
        self.assertIn("KUBERNETES", nomes); self.assertIn("SPRING", nomes)
        for l in lac:
            self.assertTrue(l["falta"]); self.assertIn("prioridade", l)
        # SPRING tem 1 repositório real (incompleto) e nenhuma medida: vem antes de KUBERNETES (zero repositórios)
        self.assertLess(nomes.index("SPRING"), nomes.index("KUBERNETES"))
        self.assertEqual([l["prioridade"] for l in lac], sorted(l["prioridade"] for l in lac))

    def test_relatorio_traz_secao_lacunas(self):
        self.assertTrue(any(l["pilha"] == "KUBERNETES" for l in relatorio()["lacunas"]))

    def test_subcomandos_ainda_nao_implementados_saem_com_2(self):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            for sub in ("reservado", "amostra", "revisao"):
                self.assertEqual(M.main([sub]), 2, sub)


class Veredito(unittest.TestCase):
    def setUp(self):
        self.prop = lj("proposta.json")
        self.sha = M.sha256_arquivo(os.path.join(FIX, "proposta.json"))
        self.antes = lj("antes.json")

    def avaliar(self, depois, prop=None, antes=None, sha=None):
        return M.avaliar(prop or self.prop, sha or self.sha, antes or self.antes, depois)

    def cli(self, depois_nome, saida):
        args = ["comparar", "--proposta", os.path.join(FIX, "proposta.json"), "--antes", os.path.join(FIX, "antes.json"),
                "--depois", os.path.join(FIX, depois_nome), "--saida", saida]
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return M.main(args)

    def test_tres_pares_dao_fica_sai_inconclusivo_com_codigos_0_1_3(self):
        with tempfile.TemporaryDirectory() as d:
            for nome, resultado, codigo in (("depois-melhora.json", "fica", 0), ("depois-piora.json", "sai", 1), ("depois-igual.json", "inconclusivo", 3)):
                s = os.path.join(d, nome)
                self.assertEqual(self.cli(nome, s), codigo, nome)
                v = json.loads(ler(s))
                self.assertEqual(v["resultado"], resultado)
                self.assertEqual(v["proposta_sha256"], self.sha)

    def test_fica_exige_tudo_ok_e_limite_inferior_positivo(self):
        v = self.avaliar(lj("depois-melhora.json"))
        self.assertEqual(v["resultado"], "fica")
        self.assertFalse(v["pos_medida"]); self.assertFalse(v["recusado"])
        self.assertGreater(v["ganho"]["ic95"][0], 0)
        self.assertTrue(all(c["ok"] for c in v["criterios"]))
        self.assertEqual({c["nome"] for c in v["criterios"]}, {"ganho_minimo", "alarme_falso", "tempo", "categorias"})

    def test_piora_de_uma_categoria_da_sai_mesmo_com_media_melhor(self):
        d = lj("depois-melhora.json")
        d["resultados"]["xss"] = [1 if i < 7 else 0 for i in range(30)]   # xss cai de 50 % para 23 %
        v = self.avaliar(d)
        media_antes = sum(sum(x) / 30 for x in self.antes["resultados"].values()) / 2
        media_depois = sum(sum(x) / 30 for x in d["resultados"].values()) / 2
        self.assertGreater(media_depois, media_antes)
        self.assertEqual(v["resultado"], "sai")
        self.assertFalse(next(c for c in v["criterios"] if c["nome"] == "categorias")["ok"])

    def test_intervalo_com_zero_ou_amostra_pequena_nunca_fica(self):
        self.assertNotEqual(self.avaliar(lj("depois-igual.json"))["resultado"], "fica")
        a, d = copy.deepcopy(self.antes), lj("depois-melhora.json")
        for x in (a, d):
            x["resultados"] = {k: v[:10] for k, v in x["resultados"].items()}
            x["falsos"] = {k: v[:10] for k, v in x["falsos"].items()}
        v = self.avaliar(d, antes=a)
        self.assertEqual(v["resultado"], "inconclusivo")
        self.assertTrue(v["amostra_pequena"])

    def test_entradas_incomparaveis_sao_recusadas_com_a_lista_do_que_difere(self):
        casos = {
            "conjunto": lambda d: d.update(conjunto="validacao"),
            "commit": lambda d: d["commits"].update({"fix/java-app": "f" * 40}),
            "ferramentas": lambda d: d["versoes_ferramentas"].update(opengrep="9.9"),
            "categorias": lambda d: d["resultados"].pop("xss"),
        }
        for nome, muda in casos.items():
            d = lj("depois-melhora.json"); muda(d)
            v = self.avaliar(d)
            self.assertTrue(v["recusado"], nome); self.assertEqual(v["resultado"], "inconclusivo", nome)
            self.assertIn(nome, v["motivo"], v["motivo"])

    def test_recusa_sai_com_codigo_3(self):
        d = lj("depois-melhora.json"); d["conjunto"] = "validacao"
        with tempfile.TemporaryDirectory() as t:
            caminho = os.path.join(t, "d.json"); M.gravar_json(caminho, d)
            args = ["comparar", "--proposta", os.path.join(FIX, "proposta.json"), "--antes", os.path.join(FIX, "antes.json"),
                    "--depois", caminho, "--saida", os.path.join(t, "v.json")]
            with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(M.main(args), 3)

    def test_hash_ausente_ou_diferente_vira_pos_medida_e_nunca_fica(self):
        for h in (None, "0" * 64):
            d = lj("depois-melhora.json"); d["proposta_sha256"] = h
            v = self.avaliar(d)
            self.assertTrue(v["pos_medida"], h); self.assertNotEqual(v["resultado"], "fica", h)

    def test_proposta_alterada_depois_da_medicao_vira_pos_medida(self):
        p = copy.deepcopy(self.prop); p["criterios"]["ganho_minimo"] = 0.01
        v = M.avaliar(p, M.sha256_bytes(json.dumps(p, sort_keys=True).encode()), self.antes, lj("depois-melhora.json"))
        self.assertTrue(v["pos_medida"]); self.assertNotEqual(v["resultado"], "fica")

    def test_tempo_acima_do_aceito_impede_fica_e_sem_limite_so_destaca(self):
        d = lj("depois-melhora.json"); d["tempo"]["agregado_s"] = 150     # +50 % contra limite de 20 %
        v = self.avaliar(d)
        self.assertNotEqual(v["resultado"], "fica")
        self.assertFalse(next(c for c in v["criterios"] if c["nome"] == "tempo")["ok"])
        p = copy.deepcopy(self.prop); p["criterios"]["tempo_max_aumento_pct"] = None
        v2 = M.avaliar(p, M.sha256_bytes(json.dumps(p, sort_keys=True).encode()), self.antes, d)
        tempo = next(c for c in v2["criterios"] if c["nome"] == "tempo")
        self.assertTrue(tempo["ok"]); self.assertEqual(tempo["valor"], 50.0); self.assertIn("sem limite", tempo["observacao"])

    def test_alarme_falso_acima_do_aceito_da_sai(self):
        d = lj("depois-melhora.json"); d["falsos"]["sqli"] = [1] * 6 + [0] * 14
        v = self.avaliar(d)
        self.assertEqual(v["resultado"], "sai")
        self.assertFalse(next(c for c in v["criterios"] if c["nome"] == "alarme_falso")["ok"])

    def test_linha_de_base_inexistente_ou_que_nao_e_a_medicao_antes_e_recusada(self):
        p = copy.deepcopy(self.prop); p["linha_de_base"] = "nao-existe"
        v = M.avaliar(p, self.sha, self.antes, lj("depois-melhora.json"))
        self.assertTrue(v["recusado"]); self.assertIn("linha_de_base", v["motivo"]); self.assertIn("nao-existe", v["motivo"])

    def test_validar_proposta_aponta_o_campo_errado(self):
        p = copy.deepcopy(self.prop); del p["linha_de_base"]; p["criterios"]["ganho_minimo"] = -1; p["semente"] = "x"
        erros = M.validar_proposta(p)
        for campo in ("linha_de_base", "ganho_minimo", "semente"):
            self.assertTrue(any(campo in e for e in erros), (campo, erros))
        self.assertEqual(M.validar_proposta(self.prop), [])

    def test_veredito_gravado_e_deterministico(self):
        with tempfile.TemporaryDirectory() as d:
            a, b = os.path.join(d, "a.json"), os.path.join(d, "b.json")
            self.cli("depois-melhora.json", a); self.cli("depois-melhora.json", b)
            self.assertEqual(ler(a, "rb"), ler(b, "rb"))


if __name__ == "__main__":
    unittest.main()
