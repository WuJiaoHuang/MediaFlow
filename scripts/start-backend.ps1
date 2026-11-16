$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (Test-Path $envFile) {
 Get-Content -LiteralPath $envFile | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
 }
}
Push-Location (Join-Path $projectRoot 'backend')
try { mvn spring-boot:run } finally { Pop-Location }
