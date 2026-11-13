<#
  Creates the MyLogBook release signing key (run once, by the app owner).

  - Asks for a password (typed by you, not shown).
  - Creates the keystore OUTSIDE the project:  ..\MyLogBook-keys\mylogbook-release.jks
  - Writes keystore.properties in the project root so Gradle can sign release builds.
    That file is in .gitignore and must never be committed.

  BACK UP the MyLogBook-keys folder and the password (e.g. Google Drive + a USB stick).
  Every future update must be signed with this same key.
#>
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$keysDir     = Join-Path (Split-Path $projectRoot -Parent) 'MyLogBook-keys'
$keystore    = Join-Path $keysDir 'mylogbook-release.jks'
$propsFile   = Join-Path $projectRoot 'keystore.properties'
$alias       = 'mylogbook'

if (Test-Path $keystore) { throw "A key already exists at $keystore. Not overwriting it." }

$keytool = Get-ChildItem "$env:USERPROFILE\.jdks" -Recurse -Filter keytool.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $keytool) { $keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue }
if (-not $keytool) { throw 'keytool.exe not found. Install a JDK or use Android Studio''s bundled JBR.' }
$keytoolPath = if ($keytool.FullName) { $keytool.FullName } else { $keytool.Source }

function Read-Plain([string]$prompt) {
    $s = Read-Host $prompt -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}
do {
    $pw  = Read-Plain 'Choose a key password (at least 8 characters)'
    $pw2 = Read-Plain 'Type it again'
    if ($pw -ne $pw2) { Write-Host 'Passwords do not match, try again.' -ForegroundColor Yellow }
    elseif ($pw.Length -lt 8) { Write-Host 'Too short, use at least 8 characters.' -ForegroundColor Yellow }
} until ($pw -eq $pw2 -and $pw.Length -ge 8)

New-Item -ItemType Directory -Force -Path $keysDir | Out-Null
& $keytoolPath -genkeypair -keystore $keystore -alias $alias -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $pw -keypass $pw -dname 'CN=MyLogBook, O=MyLogBook' | Out-Null
if (-not (Test-Path $keystore)) { throw 'keytool did not create the keystore.' }

$storeFile = $keystore -replace '\\', '/'
@(
    "storeFile=$storeFile",
    "storePassword=$pw",
    "keyAlias=$alias",
    "keyPassword=$pw"
) | Set-Content -Path $propsFile -Encoding ascii

Write-Host ''
Write-Host "Done. Key created: $keystore" -ForegroundColor Green
Write-Host "Signing settings:  $propsFile (ignored by git)"
Write-Host 'Now back up the MyLogBook-keys folder and your password.' -ForegroundColor Yellow
