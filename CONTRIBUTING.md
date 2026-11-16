# Como contribuir

O Sentinel é desenvolvido por especificação e valida tudo no CI do GitHub. Este resumo aponta para os documentos que mandam.

## Fluxo

1. **Especifique antes de codar** (funcionalidade nova): `spec.md`, `plan.md` e `tasks.md` em `specs/NNN-nome/`, com o GitHub Spec Kit. Passo a passo e a constituição com os 9 princípios: [docs/sdd.md](docs/sdd.md). Correção pequena não precisa de spec.
2. **Branch e PR**: um PR por assunto, a partir da `main`. O modelo de PR ([docs/pull_request_template.md](docs/pull_request_template.md)) pede o link da spec, os critérios de aceite e o que foi **verificado e não verificado**.
3. **Testes junto com o código**: toda funcionalidade nova leva testes. Regra nova de auditoria leva caso positivo, negativo e remediação. Não se ajusta regra sob medida para um repositório de validação.
4. **CI verde**: o merge só acontece com todos os jobs passando. Backend Java só é validado lá (Quarkus 3.40, Java 21).
5. **Registro**: toda mudança relevante entra em [docs/evolucao/LOG.md](docs/evolucao/LOG.md), dizendo o que foi e o que não foi verificado. Números citados são contagens, conferidas por `scripts/conferir-numeros.py`.
6. **Documentação**: se mudar variável, comando ou comportamento, atualize o guia correspondente e o índice [docs/README.md](docs/README.md).

## Rodar localmente

- Subir tudo: `cp .env.example .env` e `docker compose up -d --build` ([instalação](docs/guia/instalacao.md)).
- Desenvolvimento: `mvn quarkus:dev -f audit-api/pom.xml` e, em `audit-dashboard/`, `npm install && npm run dev`.
- Testes: `mvn test` na raiz e `npm test` em `audit-dashboard/`.

## Segurança

Vulnerabilidade: não abra issue pública. Veja [SECURITY.md](SECURITY.md).
