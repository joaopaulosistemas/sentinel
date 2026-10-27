# Pasta local como origem do sistema

Em vez de informar a URL do repositório, o sistema pode apontar para uma pasta que já está no servidor onde o Sentinel roda.
Útil para projetos sem acesso externo ou sem remoto Git.

## Como ligar

1. Coloque (ou monte) seus projetos em uma pasta, por exemplo `~/projetos/meu-sistema`.
2. No `.env`:
   ```
   PROJECTS_HOST_DIR=/home/voce/projetos
   SENTINEL_LOCAL_ROOT=/projects
   ```
3. `docker compose up -d --build`. O Docker monta a pasta em `/projects`, **somente leitura**.
4. Em **Sistemas**, a escolha "Pasta local" aparece no cadastro. Informe o caminho **relativo à raiz** (`meu-sistema`).
   "Verificar pasta" confirma se existe, se é um repositório Git e mostra a branch e a quantidade de arquivos.

Sem `SENTINEL_LOCAL_ROOT` o recurso fica desligado e nada muda.

## Regras

- A pasta precisa ser a **raiz de um repositório Git** (contém `.git`), dentro de `SENTINEL_LOCAL_ROOT`.
- Caminhos com `..`, fora da raiz ou symlinks que apontam para fora são recusados. As mensagens de erro não revelam caminhos do servidor.
- A auditoria roda sempre em uma **cópia temporária**, apagada ao final. A pasta original nunca é alterada.
- Arquivos acima de 10 MB são ignorados na cópia da árvore de trabalho.

## O que auditar

| Modo | O que é | Observações |
|---|---|---|
| `COMMIT` (padrão) | Último commit da branch | Tem SHA e histórico (profundidade configurável); alterações não commitadas ficam de fora |
| `TREE` | Árvore de trabalho, incluindo alterações não commitadas (respeita `.gitignore`) | Sem SHA nem histórico; a auditoria fica sem o aviso de código desatualizado e a auditoria de PR não filtra por arquivos alterados |

## Limitações

- Webhook de push não se aplica a pasta local; o agendamento por horas funciona normalmente.
- Token Git não é usado.
- Apenas repositórios Git por enquanto.

## Windows, WSL2 e Podman

- **Docker Desktop (Windows):** use caminho no formato do compose, por exemplo `PROJECTS_HOST_DIR=C:/Users/voce/projetos`.
  Pastas no sistema de arquivos do WSL2 são bem mais rápidas que `C:\`.
- **WSL2:** `PROJECTS_HOST_DIR=/home/voce/projetos`.
- **Podman:** com SELinux, acrescente `:z` ao volume (`...:/projects:ro,z`) ou ajuste o rótulo da pasta.

## "dubious ownership"

O usuário do contêiner (`audit`) normalmente não é o dono dos arquivos montados. O Sentinel já passa `safe.directory` somente para a pasta
auditada em cada comando git, sem alterar a configuração global.
