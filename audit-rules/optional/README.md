# Regras opcionais (fora do conjunto padrão)

Estas regras **não** são carregadas por padrão porque duplicam achados que o `audit-api` já gera,
o que contaria o mesmo problema duas vezes no Risk Score:

- `sentinel-secrets.yml` — o `SecretsAnalyzer` já detecta senhas/chaves/tokens (com filtro de placeholders,
  mais tipos de arquivo e formatos conhecidos como AWS/GitHub/Slack).
- `sentinel-logging.yml` — o `ArchitectureAnalyzer` já agrega `System.out/err` e `printStackTrace()`
  como achados de Observabilidade, um por arquivo.

Para usá-las em uma análise manual com o Semgrep:

    semgrep --config audit-rules/packs/core-security/rules --config audit-rules/packs/core-quality/rules --config audit-rules/optional --metrics=off <pasta>
