param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Output = (Join-Path $PSScriptRoot 'dist'),
    [string]$KeyStore = '',
    [string]$KeyAlias = 'stopwatch',
    [switch]$Release
)
$ErrorActionPreference = 'Stop'
if ($Release) {
    if (!$KeyStore -or !(Test-Path -LiteralPath $KeyStore)) { throw 'A release requires an existing -KeyStore.' }
    if (!$env:STOPWATCH_STORE_PASSWORD) { throw 'Set STOPWATCH_STORE_PASSWORD before signing a release.' }
    $storePassword = $env:STOPWATCH_STORE_PASSWORD
    $keyPassword = $env:STOPWATCH_KEY_PASSWORD
    if (!$keyPassword) { $keyPassword = $storePassword }
} else {
    if (!$KeyStore) { $KeyStore = Join-Path $PSScriptRoot '.signing\development.jks' }
    $storePassword = 'android'
    $keyPassword = 'android'
}
$KeyStore = [IO.Path]::GetFullPath($KeyStore)
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk -and $env:LOCALAPPDATA) {
    $localSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $localSdk) { $Sdk = $localSdk }
}
if (!$Sdk) { throw 'Pass -Sdk with the Android SDK path (platform 36, build-tools 36.0.0).' }
$tools = Join-Path $Sdk 'build-tools\36.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-36\android.jar'
$Output = [IO.Path]::GetFullPath($Output)
Push-Location -LiteralPath $PSScriptRoot
$previousStorePassword = $env:STOPWATCH_BUILD_STORE_PASSWORD
$previousKeyPassword = $env:STOPWATCH_BUILD_KEY_PASSWORD
$env:STOPWATCH_BUILD_STORE_PASSWORD = $storePassword
$env:STOPWATCH_BUILD_KEY_PASSWORD = $keyPassword
try {
    $build = 'build'
    $main = 'app\src\main'
    foreach ($p in @($tools, $androidJar)) {
        if (!(Test-Path -LiteralPath $p)) { throw "Missing SDK component: $p" }
    }
    foreach ($p in @($build, $Output, (Join-Path $build 'classes'), (Join-Path $build 'tests'),
            (Join-Path $build 'dex'), (Join-Path $build 'generated'), (Split-Path $KeyStore))) {
        New-Item -ItemType Directory -Path $p -Force | Out-Null
    }
    function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
        & $Tool @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
    }
    $model = Join-Path $main 'java\app\quiet\stopwatch\Stopwatch.java'
    $taps = Join-Path $main 'java\app\quiet\stopwatch\TapController.java'
    $pixelShift = Join-Path $main 'java\app\quiet\stopwatch\PixelShift.java'
    Invoke-Checked 'javac' @('-encoding', 'UTF-8', '-d', (Join-Path $build 'tests'),
        $model, $taps, $pixelShift, (Join-Path $PSScriptRoot 'tests\StopwatchTest.java'))
    Invoke-Checked 'java' @('-cp', (Join-Path $build 'tests'), 'app.quiet.stopwatch.StopwatchTest')
    Invoke-Checked (Join-Path $tools 'aapt2.exe') @('compile', '--dir', (Join-Path $main 'res'),
        '-o', (Join-Path $build 'compiled-res.zip'))
    Invoke-Checked (Join-Path $tools 'aapt2.exe') @('link', '-I', $androidJar,
        '--manifest', (Join-Path $main 'AndroidManifest.xml'), '--java', (Join-Path $build 'generated'),
        '-A', (Join-Path $main 'assets'),
        '-o', (Join-Path $build 'resources.apk'), (Join-Path $build 'compiled-res.zip'))
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $main 'java'), (Join-Path $build 'generated') -Recurse -Filter '*.java' | ForEach-Object FullName)
    Invoke-Checked 'javac' (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath',
        $androidJar, '-d', (Join-Path $build 'classes')) + $sources)
    Invoke-Checked 'jar' @('cf', (Join-Path $build 'classes.jar'), '-C', (Join-Path $build 'classes'), '.')
    Invoke-Checked (Join-Path $tools 'd8.bat') @('--release', '--min-api', '26', '--lib', $androidJar,
        '--output', (Join-Path $build 'dex'), (Join-Path $build 'classes.jar'))
    Copy-Item -LiteralPath (Join-Path $build 'resources.apk') -Destination (Join-Path $build 'unsigned.apk') -Force
    Invoke-Checked 'jar' @('uf', (Join-Path $build 'unsigned.apk'), '-C', (Join-Path $build 'dex'), 'classes.dex')
    Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-f', '-P', '16', '4',
        (Join-Path $build 'unsigned.apk'), (Join-Path $build 'aligned.apk'))
    if (!(Test-Path -LiteralPath $KeyStore)) {
        Invoke-Checked 'keytool' @('-genkeypair', '-keystore', $KeyStore,
            '-storepass:env', 'STOPWATCH_BUILD_STORE_PASSWORD',
            '-keypass:env', 'STOPWATCH_BUILD_KEY_PASSWORD', '-alias', $KeyAlias,
            '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
            '-dname', 'CN=AMOLED Stopwatch Development')
    }
    $apkName = if ($Release) { 'AmoledStopwatch.apk' } else { 'AmoledStopwatch-dev.apk' }
    $apk = Join-Path $Output $apkName
    Invoke-Checked (Join-Path $tools 'apksigner.bat') @('sign', '--ks', $KeyStore,
        '--ks-key-alias', $KeyAlias, '--ks-pass', 'env:STOPWATCH_BUILD_STORE_PASSWORD',
        '--key-pass', 'env:STOPWATCH_BUILD_KEY_PASSWORD',
        '--out', $apk, (Join-Path $build 'aligned.apk'))
    Invoke-Checked (Join-Path $tools 'apksigner.bat') @('verify', '--verbose', $apk)
    Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-c', '-P', '16', '4',
        [IO.Path]::GetRelativePath($PSScriptRoot, $apk))
    Get-Item -LiteralPath $apk | Select-Object FullName, Length | Format-List
    Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List
} finally {
    $env:STOPWATCH_BUILD_STORE_PASSWORD = $previousStorePassword
    $env:STOPWATCH_BUILD_KEY_PASSWORD = $previousKeyPassword
    Pop-Location
}
