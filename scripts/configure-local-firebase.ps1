[CmdletBinding()]
param(
    [string]$DeviceName = "Meu computador",
    [int]$DashboardPort = 8765
)

$ErrorActionPreference = "Stop"

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$googleServicesPath = Join-Path $workspaceRoot "android\app\google-services.json"
if (-not (Test-Path -LiteralPath $googleServicesPath)) {
    throw "Arquivo ausente: $googleServicesPath"
}

$googleServices = Get-Content -LiteralPath $googleServicesPath -Raw | ConvertFrom-Json
$projectId = [string]$googleServices.project_info.project_id
$apiKey = [string]$googleServices.client[0].api_key[0].current_key
if ([string]::IsNullOrWhiteSpace($projectId) -or [string]::IsNullOrWhiteSpace($apiKey)) {
    throw "google-services.json não contém project_id e api_key válidos"
}

$configDirectory = Join-Path $env:LOCALAPPDATA "AntigravityRemote"
$configPath = Join-Path $configDirectory "config.json"
New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null

$config = [ordered]@{
    firebase_database_url = "https://$projectId-default-rtdb.firebaseio.com"
    firebase_api_key = $apiKey
    functions_base_url = "https://us-central1-$projectId.cloudfunctions.net"
    firebase_storage_bucket = "$projectId.firebasestorage.app"
    device_name = $DeviceName
    dashboard_port = $DashboardPort
}

$json = $config | ConvertTo-Json
[System.IO.File]::WriteAllText($configPath, $json, [System.Text.UTF8Encoding]::new($false))
Write-Host "Configuração local criada em $configPath"
