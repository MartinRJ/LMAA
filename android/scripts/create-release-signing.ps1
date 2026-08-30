[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$androidRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingDirectory = [System.IO.Path]::GetFullPath((Join-Path $androidRoot 'signing'))
$keystorePath = [System.IO.Path]::GetFullPath((Join-Path $signingDirectory 'lmaa-release.jks'))
$propertiesPath = [System.IO.Path]::GetFullPath((Join-Path $androidRoot 'signing.properties'))

if (-not $keystorePath.StartsWith($androidRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Ungültiger Keystore-Zielpfad.'
}
if (-not $propertiesPath.StartsWith($androidRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Ungültiger Properties-Zielpfad.'
}
if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'Release-Signing existiert bereits; nichts wurde überschrieben.'
}

$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$randomBytes = New-Object byte[] 36
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($randomBytes)
} finally {
    $random.Dispose()
}
$password = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$env:LMAA_RELEASE_STORE_PASSWORD = $password
$env:LMAA_RELEASE_KEY_PASSWORD = $password
try {
    & $keytool -genkeypair -noprompt `
        -keystore $keystorePath `
        -storetype PKCS12 `
        -alias 'lmaa-release' `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname 'CN=Local Media Analysis Assistant, OU=Personal Sideload, O=LMAA, C=DE' `
        -storepass:env LMAA_RELEASE_STORE_PASSWORD `
        -keypass:env LMAA_RELEASE_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) {
        throw "keytool ist mit Exitcode $LASTEXITCODE fehlgeschlagen."
    }

    $properties = @(
        'storeFile=signing/lmaa-release.jks'
        "storePassword=$password"
        'keyAlias=lmaa-release'
        "keyPassword=$password"
    ) -join [Environment]::NewLine
    [System.IO.File]::WriteAllText(
        $propertiesPath,
        $properties + [Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false)
    )
} catch {
    if (Test-Path -LiteralPath $keystorePath) {
        Remove-Item -LiteralPath $keystorePath -Force
    }
    if (Test-Path -LiteralPath $propertiesPath) {
        Remove-Item -LiteralPath $propertiesPath -Force
    }
    throw
} finally {
    Remove-Item Env:LMAA_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LMAA_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    $password = $null
}

Write-Output 'Release-Keystore und signing.properties wurden lokal erstellt.'
Write-Output 'Beide Dateien gemeinsam sicher außerhalb des Repositorys sichern.'
