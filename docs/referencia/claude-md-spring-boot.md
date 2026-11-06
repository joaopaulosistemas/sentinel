> Referência: exemplo do CLAUDE.md que o Sentinel gera para Spring Boot (endpoint `/api/v1/guide/spring/claude-md`, texto em `GuideBook.claudeMd`). Copiado em 2026-11-06 e corrigido à mão para o que o gerador 12.64.0 produz: sem a regra de Android e sem a linha repetida do Actuator. As regras de "Sem exceção" mudam com os pacotes: para o texto vigente, use o endpoint.

# Regras de código — Spring Boot

Este sistema é auditado pelo Sentinel. Aqui fica só o que não dá para deduzir do código.

## Verifique antes de dizer que terminou

- `mvn -B -ntp verify`
- `mvn -Dtest=NomeTest test`
- Só diga que terminou com tudo isso passando; se algo falhar, corrija ou diga o que falhou.

## Convenções

- Injeção por construtor: Dependências como campos final, injetadas pelo construtor.
- Entrada validada: @Valid em todo @RequestBody e restrições nos DTOs.
- Configuração tipada: @ConfigurationProperties com validação; segredos fora do repositório.
- JPA sem surpresa: Relações LAZY por padrão; busque o que precisa com fetch join ou projeção.
- Transação no serviço: @Transactional na camada de serviço, o mais estreita possível.
- Erro padronizado: @RestControllerAdvice com ProblemDetail (RFC 9457).

## Armadilhas

- @Autowired em campo: esconde dependências e dificulta o teste.
- Receber a entidade JPA direto na API (mass assignment).
- Senha em application.properties e actuator exposto sem proteção.
- EAGER, @Data em entidade e ddl-auto=update em produção.
- Não copie padrões do código legado para código novo.

## Sem exceção (severidade alta no Sentinel)

- Todos os endpoints do Actuator estão expostos pela web (inclui env, heapdump, beans) → Exponha só health e info; proteja o restante com autenticação
- Console do H2 habilitado → Desligue o console do H2 fora do perfil de desenvolvimento local
- anyRequest().permitAll() libera todas as rotas que você não listou antes → Termine a cadeia com anyRequest().authenticated() e liste as rotas públicas antes
- Credencial escrita direto no arquivo de configuração → Leia a credencial de variável de ambiente ou cofre de segredos e troque a que já foi versionada
- Chave de assinatura JWT escrita no código → A chave de assinatura não pode estar no código: leia de cofre/variável e permita rotação
- JWT lido sem verificar a assinatura (parse/decode) → Valide a assinatura, o emissor e a expiração do token antes de confiar nas claims

## Limites

- Função: até 60 linhas, complexidade 12, aninhamento 4. Até 5 parâmetros.
- Classe: até 500 linhas. Sem números mágicos.

## Como trabalhar

- Faça a solução mais simples que resolve o problema de hoje.
- Todo comportamento novo vem com teste que falha se o código quebrar.
- Toda chamada remota tem timeout; repita só operação idempotente, com espera exponencial e jitter.
- Exceção a uma regra: registre no `.sentinel.yml` com motivo e prazo.

## Commits e revisão

- Commit: `tipo(escopo): descrição` no imperativo, até 72 caracteres (feat, fix, docs, refactor, test, chore). O corpo explica o porquê.
- Um assunto por mudança; refatoração separada de comportamento.
- Se algo ficou fora do escopo, abra uma issue em vez de deixar "depois eu arrumo".
- Ao revisar, comente o código (não a pessoa) e marque `Nit:`, `Opcional:` ou `FYI:`.
