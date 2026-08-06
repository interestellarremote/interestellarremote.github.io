$ErrorActionPreference = 'Stop'
$rootDir = $PSScriptRoot
Set-Location -LiteralPath $rootDir

Write-Host "1. Building PyInstaller executable..."
Set-Location -LiteralPath (Join-Path $rootDir "bridge\scripts")
.\build_windows.ps1
Set-Location -LiteralPath $rootDir

Write-Host "2. Copying to dist-publish..."
if (-not (Test-Path -LiteralPath "bridge\dist-publish")) { New-Item -ItemType Directory -Path "bridge\dist-publish" | Out-Null }
Remove-Item -Recurse -Force bridge\dist-publish\AntigravityRemote -ErrorAction SilentlyContinue
Copy-Item -Recurse bridge\dist\AntigravityRemote bridge\dist-publish\AntigravityRemote

Write-Host "3. Running Inno Setup..."
$iscc = "C:\Users\ronal\AppData\Local\Programs\Inno Setup 6\ISCC.exe"
& $iscc bridge\installer\AntigravityRemote.iss

Write-Host "4. Generating SHA-256..."
$exePath = "bridge\installer\Output\InterestellarRemoteSetup-0.2.7.exe"
if (-not (Test-Path $exePath)) { throw "Installer not generated!" }

$hash = (Get-FileHash -Algorithm SHA256 -Path $exePath).Hash
$sizeBytes = (Get-Item $exePath).Length
$sizeFormatted = "{0:N0}" -f $sizeBytes

Write-Host "Hash: $hash"
Write-Host "Size: $sizeBytes ($sizeFormatted)"

Write-Host "5. Moving to downloads folder..."
Copy-Item -Force $exePath downloads\
Set-Content -Path downloads\SHA256SUMS.txt -Value "$hash *InterestellarRemoteSetup-0.2.7.exe"

Write-Host "6. Updating html with new size and hash..."
$indexPaths = @("index.html", "site\index.html")
foreach ($idx in $indexPaths) {
    $content = Get-Content $idx -Raw
    $content = $content -replace "<strong>[\d,.]+ bytes</strong>", "<strong>$sizeFormatted bytes</strong>"
    $content = $content -replace "<pre>[A-F0-9]{64}</pre>", "<pre>$hash</pre>"
    Set-Content -Path $idx -Value $content
}

Write-Host "Done! The new version 0.2.7 is ready in the downloads folder and html is updated."
