# Coleta as evidências de banco para o Sentinel no Windows (PowerShell). Nada é alterado no banco.
# Uso:  .\coletar-evidencias.ps1 -Url "postgresql://usuario@servidor:5432/banco" [-Saida .\saida-evidencias]
# Precisa de pg_dump e psql no PATH (instalador do PostgreSQL ou "scoop install postgresql").
param(
  [Parameter(Mandatory = $true)][string]$Url,
  [string]$Saida = ".\saida-evidencias"
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Saida | Out-Null
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

# Esquema: só as definições (tabelas, chaves, índices). --schema-only garante que nenhum dado sai do banco.
pg_dump --schema-only --no-owner --no-privileges --no-comments $Url | Out-File -Encoding utf8 (Join-Path $Saida "schema.sql")

# Estatísticas de uso (consultas caras, tabelas, índices).
Push-Location $Saida
try { psql $Url -f (Join-Path $aqui "coletar-evidencias.sql") } finally { Pop-Location }

Write-Host "Pronto. Copie o conteúdo de '$Saida' para .\evidencias\<nome-do-sistema>\"
