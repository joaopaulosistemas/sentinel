# Audita o repositório Frequency no Sentinel.
# Uso (na pasta do projeto, onde está o .env):  .\auditar-frequency.ps1
# Parâmetros opcionais:  -Branch main  -Api http://localhost:8080
param(
  [string]$Repo   = "https://github.com/joaopaulosistemas/frequency.git",
  [string]$Nome   = "Frequency",
  [string]$Branch = "main",
  [string]$Api    = "http://localhost:8080",
  [string]$EnvFile = ".\.env"
)
$ErrorActionPreference = "Stop"

# Cabeçalhos (usa a API_KEY do .env, se houver)
$h = @{ "Content-Type" = "application/json" }
if (Test-Path $EnvFile) {
  $m = Select-String -Path $EnvFile -Pattern "^API_KEY=(.+)$"
  if ($m) { $h["X-API-Key"] = $m.Matches[0].Groups[1].Value.Trim() }
}

# Repositório privado precisa de GIT_TOKEN no .env
$temToken = (Test-Path $EnvFile) -and (Select-String -Path $EnvFile -Pattern "^GIT_TOKEN=.+" -Quiet)
if (-not $temToken) {
  Write-Warning "GIT_TOKEN vazio no .env. Se o repositório for privado, a auditoria vai falhar no clone."
}

# API no ar?
try { Invoke-RestMethod "$Api/api/v1/health" | Out-Null }
catch { throw "API não responde em $Api. Rode: docker compose ps" }

# Cadastra o sistema (reaproveita se já existir com esse nome)
$sistema = $null
try {
  $lista = Invoke-RestMethod "$Api/api/v1/systems" -Headers $h
  $sistema = @($lista) | Where-Object { $_.name -eq $Nome } | Select-Object -First 1
} catch { }
if (-not $sistema) {
  $body = @{ name = $Nome; description = "PWA de ingles"; repoUrl = $Repo; stack = "JAVASCRIPT"; team = "Pessoal" } | ConvertTo-Json
  $sistema = Invoke-RestMethod -Method Post "$Api/api/v1/systems" -Headers $h -Body $body
  Write-Host "Sistema cadastrado: id $($sistema.id)"
} else {
  Write-Host "Sistema já existe: id $($sistema.id)"
}

# Dispara a auditoria
$body = @{ systemId = $sistema.id; branch = $Branch; triggeredBy = $env:USERNAME } | ConvertTo-Json
$run = Invoke-RestMethod -Method Post "$Api/api/v1/audits" -Headers $h -Body $body
Write-Host "Auditoria #$($run.id) iniciada no branch '$Branch'. Aguardando (até 15 min)..."

# Aguarda o fim
$fim = (Get-Date).AddMinutes(15)
do {
  Start-Sleep -Seconds 10
  $run = Invoke-RestMethod "$Api/api/v1/audits/$($run.id)" -Headers $h
  Write-Host ("  status: " + $run.status)
} while ($run.status -in @("PENDING", "RUNNING") -and (Get-Date) -lt $fim)

if ($run.status -ne "COMPLETED") {
  Write-Host "Auditoria terminou como $($run.status): $($run.errorMessage)" -ForegroundColor Red
  exit 1
}

# Resultado
$in = Invoke-RestMethod "$Api/api/v1/audits/$($run.id)/insights" -Headers $h
Write-Host ""
Write-Host "Score geral: $($run.riskScore.overall)" -ForegroundColor Cyan
Write-Host $in.verdict -ForegroundColor Yellow
Write-Host $in.executiveSummary
Write-Host ""
Write-Host "Dívida técnica (top 10):"
$in.topProblems | Select-Object priority, problem, occurrences, effort, areaLabel | Format-Table -AutoSize -Wrap
Write-Host "Quick wins:"
$in.quickWins | Select-Object problem, recommendation | Format-Table -AutoSize -Wrap

# Relatório HTML para abrir no navegador
$arq = Join-Path (Get-Location) "relatorio-$Nome-$($run.id).html"
Invoke-WebRequest "$Api/api/v1/audits/$($run.id)/report" -Headers $h -OutFile $arq
Write-Host "Relatório salvo em $arq"
Write-Host "Dashboard: http://localhost:3000"
