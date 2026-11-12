# Guia de validação (quickstart): Medição e prova

Cenários que provam, de ponta a ponta, que a funcionalidade funciona. Rodam em qualquer máquina com Python 3 e `git` (inclusive nesta sessão); só o passo 6 exige o GitHub. Formatos em [contracts/arquivos.md](contracts/arquivos.md); comandos em [contracts/cli.md](contracts/cli.md).

## Pré-requisitos

- Repositório clonado na `main` com a fatia correspondente aplicada.
- Dados de teste em `scripts/fixtures/medicao/` (medidas e gabaritos pequenos e conhecidos).

## 1. Os testes de unidade passam (Fatias A a C e E)

```bash
python3 -I scripts/test_medicao.py
python3 -I scripts/test_verificar_separacao.py
```

Esperado: todos os testes passam; nenhum depende de rede.

## 2. Relatório de linha de base é repetível (US1, SC-002)

```bash
python3 -I scripts/medicao.py relatorio --conjunto treino --medidas scripts/fixtures/medicao/medidas --saida /tmp/m1
python3 -I scripts/medicao.py relatorio --conjunto treino --medidas scripts/fixtures/medicao/medidas --saida /tmp/m2
diff -r /tmp/m1 /tmp/m2
```

Esperado: `diff` sem saída; no `.md`, cada pilha do escopo aparece com número ou "sem medida" e motivo; toda taxa tem total e intervalo; amostra abaixo de 20 traz o aviso.

## 3. Veredito com três mudanças de teste (US2, SC-004)

```bash
for caso in melhora piora igual; do
  python3 -I scripts/medicao.py comparar --proposta scripts/fixtures/medicao/propostas/$caso.json \
    --antes scripts/fixtures/medicao/antes.json --depois scripts/fixtures/medicao/depois-$caso.json --saida /tmp/v-$caso.json
  echo "$caso -> saída $?"
done
```

Esperado: `melhora` → "fica" (0), `piora` → "sai" (1), `igual` → "inconclusivo" (3). Com a proposta alterada depois da medição, o resultado é marcado `pos_medida` e nunca "fica".

## 4. Comparação incomparável é recusada (US2, cenário 4)

Trocar o `commit` de um repositório no `depois` e rodar `comparar`: saída 3 e mensagem dizendo o que difere.

## 5. O reservado é protegido (US3, SC-005, SC-006)

```bash
python3 -I scripts/verificar-separacao.py
```

Esperado: sem problemas. Em seguida, em uma cópia, (a) mover um repositório reservado para `treino` sem registro, (b) citar o nome dele num arquivo fora da lista permitida e (c) editar uma linha antiga de `consultas.json`: cada um dá `problemas > 0` e saída 1.

## 6. Linha de base real (Fatia D, só no GitHub)

Disparar o workflow `Medição` (`medicao.yml`, pede autorização do João). Esperado: artefato `linha-de-base` com as medições de treino, validação e uma consulta ao reservado no marco 0, registrada em `consultas.json`; o CI existente segue verde.

## 7. Precisão em código real (US4, SC-008)

```bash
python3 -I scripts/medicao.py amostra --codigo-real audit-rules/calibracao/codigo-real.json --achados <pasta> --n 100 --semente 20261112 --saida revisao-humana.md
python3 -I scripts/medicao.py revisao --arquivo docs/evolucao/revisao-humana.md
```

Esperado: com vereditos de teste preenchidos, precisão por regra com intervalo, pendentes contados, "sem evidência" abaixo do mínimo e concordância calculada com a lista de divergências. Com os vereditos reais do João, SC-008 é dado como atendido.

## Critério de pronto

Passos 1 a 5 verdes no CI; passo 6 com a linha de base publicada e aprovada pelo João; passo 7 depende do tempo humano e fica registrado como pendente até lá.
