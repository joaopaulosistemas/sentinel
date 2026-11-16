# Política de segurança

## Como reportar uma vulnerabilidade

Não abra issue pública com detalhes. Envie ao mantenedor do repositório (`joaopaulosistemas`, no GitHub) uma mensagem privada com:

- o que foi encontrado e o impacto;
- passos para reproduzir (versão, configuração, requisição);
- se possível, uma sugestão de correção.

Se o repositório tiver o relato privado de vulnerabilidades do GitHub habilitado (aba **Security**), use-o.

## Escopo

Entra: a API, o painel, a CLI, as imagens do Compose e o chart Helm, e os pacotes de regras embutidos.
Não entra: falhas de ferramentas externas que o Sentinel só executa (Opengrep, Trivy, Gitleaks, PMD, ESLint), que devem ir aos projetos delas; configuração insegura de quem instala (por exemplo, `ALLOW_OPEN_API=true` fora de desenvolvimento).

## O que o Sentinel já faz

- A API recusa subir sem `API_KEY` ou OIDC (exceto `ALLOW_OPEN_API=true`, só para desenvolvimento).
- Tokens Git, segredos de webhook e URLs de notificação ficam cifrados no banco e nunca voltam pela API.
- Repositórios em rede interna são recusados, salvo os hosts de `ALLOWED_INTERNAL_HOSTS` (anti-SSRF); no Helm há `NetworkPolicy`.
- O código auditado nunca é compilado nem executado. Pacotes que rodam ferramentas externas só funcionam se liberados (`PACKS_ALLOW_TOOLS`) e podem exigir assinatura.
- Todos os serviços do Compose escutam só em `127.0.0.1`.

## Boas práticas ao instalar

Gere `API_KEY` e `SECRETS_KEY` com `openssl rand -hex 32`, não versione o `.env`, faça [backup](docs/guia/operacao.md) da chave de cifra e desligue o laboratório de regras (`PLAYGROUND_ENABLED=false`) em instalação compartilhada.
