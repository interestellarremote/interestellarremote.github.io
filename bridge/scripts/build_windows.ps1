$ErrorActionPreference = 'Stop'
$BridgeRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $BridgeRoot
if (-not (Test-Path -LiteralPath '.venv')) { python -m venv .venv }
.\.venv\Scripts\python.exe -m pip install -e . pyinstaller
if ($LASTEXITCODE -ne 0) { throw "Falha ao instalar as dependências da ponte" }
.\.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --name AntigravityRemote `
  --collect-all pystray --hidden-import win32timezone `
  --windowed launcher.py
if ($LASTEXITCODE -ne 0) { throw "Falha ao gerar o executável da ponte" }
Write-Host "Executável criado em $BridgeRoot\dist\AntigravityRemote"
