$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
Push-Location "$RepoRoot\bridge"
try {
  .\.venv\Scripts\python.exe -m pytest -q
  .\.venv\Scripts\ruff.exe check src tests
} finally { Pop-Location }
Push-Location "$RepoRoot\firebase\functions"
try {
  npm run build
  npm audit
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  npm run test:rules
} finally { Pop-Location }
Push-Location "$RepoRoot\android"
try { .\gradlew.bat testDebugUnitTest } finally { Pop-Location }
