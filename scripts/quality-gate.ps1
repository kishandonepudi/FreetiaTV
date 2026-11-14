<#
  Deterministic release-gate checks (standards/RELEASE_GATE.md, checkpoints C1-C7, C11).
  Usage:  .\scripts\quality-gate.ps1          # full gate (includes R8 release build)
          .\scripts\quality-gate.ps1 -Fast    # unit tests + static checks only
  Writes build/quality-gate.json and exits non-zero on any failure.
#>
param([switch]$Fast)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

$results = [ordered]@{}
function Check([string]$id, [string]$name, [scriptblock]$body) {
    Write-Host "[$id] $name ... " -NoNewline
    try {
        $detail = & $body
        $results[$id] = @{ name = $name; pass = $true; detail = "$detail" }
        Write-Host "PASS" -ForegroundColor Green
    } catch {
        $results[$id] = @{ name = $name; pass = $false; detail = $_.Exception.Message }
        Write-Host "FAIL: $($_.Exception.Message)" -ForegroundColor Red
    }
}
function Gradle([string[]]$tasks) {
    $log = Join-Path $env:TEMP "freetia-gate-$($tasks[0]).log"
    # PowerShell 5.1 turns redirected native stderr into ErrorRecords, which 'Stop' makes terminating even on
    # exit code 0 (Gradle warnings go to stderr). Let cmd do the redirection and trust only the exit code.
    $ErrorActionPreference = 'Continue'
    cmd /c "`"$root\gradlew.bat`" $($tasks -join ' ') --console=plain -q > `"$log`" 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "gradle $($tasks -join ' ') failed (exit $LASTEXITCODE); see $log" }
}

# Returns the text between the braces that follow the first match of $header, honouring nesting.
function Get-Block([string]$text, [string]$header) {
    $m = [regex]::Match($text, $header + '\s*\{')
    if (-not $m.Success) { return $null }
    $start = $m.Index + $m.Length
    $depth = 1
    for ($i = $start; $i -lt $text.Length; $i++) {
        if ($text[$i] -eq '{') { $depth++ }
        elseif ($text[$i] -eq '}') { $depth--; if ($depth -eq 0) { return @{ inner = $text.Substring($start, $i - $start); from = $m.Index; to = $i + 1 } } }
    }
    $null
}

function Test-StoreHasNoDefaultPlaylist([string]$gradleFile) {
    $gradle = Get-Content $gradleFile -Raw
    $store = Get-Block $gradle 'create\("store"\)'
    $foss = Get-Block $gradle 'create\("foss"\)'
    if (-not $store) { throw 'store flavor block not found' }
    $fieldRe = 'buildConfigField\(\s*"[^"]*"\s*,\s*"(DEFAULT_PLAYLIST_\w+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)'
    $fields = [regex]::Matches($store.inner, $fieldRe)
    if ($fields.Count -eq 0) { throw 'store flavor defines no DEFAULT_PLAYLIST_* fields' }
    if (-not ($fields | Where-Object { $_.Groups[1].Value -eq 'DEFAULT_PLAYLIST_URL' })) { throw 'store flavor does not define DEFAULT_PLAYLIST_URL' }
    # The Kotlin literal for an empty Java string is "\"\"", i.e. the raw text \"\".
    $nonEmpty = @($fields | Where-Object { $_.Groups[2].Value -ne '\"\"' } | ForEach-Object { $_.Groups[1].Value })
    if ($nonEmpty.Count -gt 0) { throw "store flavor sets non-empty $($nonEmpty -join ', ')" }
    # Anything outside the two flavor blocks (defaultConfig, buildTypes) would be inherited by store.
    $outside = $gradle
    foreach ($b in @($store, $foss) | Where-Object { $_ } | Sort-Object { $_.from } -Descending) {
        $outside = $outside.Remove($b.from, $b.to - $b.from)
    }
    if ($outside -match 'DEFAULT_PLAYLIST_\w+') { throw "$($Matches[0]) is defined outside the flavor blocks (store would inherit it)" }
    "store: $($fields.Count) DEFAULT_PLAYLIST_* fields, all empty"
}

function Test-NoSensitiveLogging($files) {
    # Skips whole nested (...) groups, or descends one level into the last one, so the match stays inside the
    # call's arguments, e.g. Log.d(TAG, "x ${fmt(channel.url)}") spanning several lines.
    $callArgs = '\((?:[^()]|\([^()]*\))*?(?:\([^()]*?)?(url|token|pin)\b'
    $patterns = @("(?is)\bLog\.(?:[dewiv]|wtf)\s*$callArgs", "(?is)\b(?:println|print|Timber\.\w+)\s*$callArgs")
    $hits = foreach ($f in $files) {
        $text = Get-Content $f.FullName -Raw
        if (-not $text) { continue }
        foreach ($p in $patterns) {
            foreach ($m in [regex]::Matches($text, $p)) {
                "$($f.Name):$(($text.Substring(0, $m.Index) -split "`n").Count)"
            }
        }
    }
    @($hits | Select-Object -Unique)
}

function Test-ManifestHygiene([string]$manifestFile, [string]$package) {
    $ns = 'http://schemas.android.com/apk/res/android'
    [xml]$doc = Get-Content $manifestFile -Raw
    $app = $doc.manifest.application
    if (-not $app -or $app.GetAttribute('allowBackup', $ns) -ne 'false') { throw 'allowBackup must be "false"' }
    # Keep in sync with standards/SECURITY_STANDARDS.md S5; adding an exported component needs a reviewed reason.
    $allowedExported = @('.ui.MainActivity', '.playback.PlaybackService', '.receiver.BootReceiver')
    $exported = foreach ($node in $app.ChildNodes) {
        if ($node.NodeType -ne 'Element' -or $node.LocalName -notin 'activity', 'activity-alias', 'service', 'receiver', 'provider') { continue }
        $flag = $node.GetAttribute('exported', $ns)
        # With no explicit flag, an intent-filter makes the component exported.
        $isExported = $flag -eq 'true' -or (-not $flag -and $node.SelectNodes('intent-filter').Count -gt 0)
        if (-not $isExported) { continue }
        $name = $node.GetAttribute('name', $ns)
        if ($name.StartsWith("$package.")) { $name = $name.Substring($package.Length) }
        elseif (-not $name.StartsWith('.') -and -not $name.Contains('.')) { $name = ".$name" }
        $name
    }
    $unexpected = @($exported | Where-Object { $_ -notin $allowedExported })
    if ($unexpected.Count -gt 0) { throw "unreviewed exported component(s): $($unexpected -join ', ')" }
    "exported: $($exported -join ', ')"
}

$src = 'app/src/main'
$kotlin = Get-ChildItem "$src/kotlin" -Recurse -Filter *.kt

Check 'C2' 'Unit tests' {
    Gradle @('testFossDebugUnitTest')
    $xml = @(Get-ChildItem app/build/test-results/testFossDebugUnitTest -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $tests = 0; $bad = 0
    foreach ($x in $xml) {
        $suite = ([xml](Get-Content $x.FullName -Raw)).testsuite
        $tests += [int]$suite.tests
        $bad += [int]$suite.failures + [int]$suite.errors
    }
    if ($tests -eq 0) { throw 'no unit tests ran (app/build/test-results/testFossDebugUnitTest is empty)' }
    if ($bad -gt 0) { throw "$bad failing test(s)" }
    "$tests tests"
}

if (-not $Fast) {
    Check 'C1' 'Debug builds (foss + store)' { Gradle @('assembleFossDebug', 'assembleStoreDebug'); 'ok' }
    Check 'C3' 'Lint: 0 errors' {
        Gradle @('lintFossDebug')
        $report = Get-ChildItem app/build/reports -Recurse -Filter 'lint-results-fossDebug.xml' | Select-Object -First 1
        $errors = @(([xml](Get-Content $report.FullName)).issues.issue | Where-Object { $_.severity -in 'Error', 'Fatal' })
        if ($errors.Count -gt 0) { throw "$($errors.Count) lint error(s): " + (($errors | ForEach-Object { $_.id }) -join ', ') }
        'ok'
    }
}

Check 'C4' 'Store flavor has no default playlist' { Test-StoreHasNoDefaultPlaylist 'app/build.gradle.kts' }

Check 'C5' 'Brand assets in place' {
    foreach ($f in 'drawable/ic_brand_mark.xml', 'drawable/ic_launcher_foreground.xml', 'drawable/ic_launcher_background.xml', 'drawable/tv_banner.xml') {
        if (-not (Test-Path "$src/res/$f")) { throw "missing res/$f" }
    }
    $icon = Get-Content "$src/res/mipmap-anydpi-v26/ic_launcher.xml" -Raw
    if ($icon -notmatch 'ic_launcher_background' -or $icon -notmatch 'ic_launcher_foreground') { throw 'adaptive icon does not use brand layers' }
    if (-not ($kotlin | Select-String -Pattern 'R\.drawable\.ic_brand_mark' -Quiet)) { throw 'no screen shows ic_brand_mark' }
    'ok'
}

Check 'C6' 'No secrets or URL logging' {
    $secretFiles = Get-ChildItem -Recurse -File -Include *.jks, *.keystore, *.p12, google-services.json -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\build\\|\\.gradle\\' }
    if ($secretFiles) { throw "secret files in tree: $($secretFiles.Name -join ', ')" }
    $leaks = Test-NoSensitiveLogging $kotlin
    if ($leaks.Count -gt 0) { throw "possible URL/PIN/token logging: $($leaks -join ', ')" }
    $hardcoded = $kotlin | Select-String -Pattern '(password|secret|api[_-]?key|token)\s*=\s*"[^"]{6,}"' -CaseSensitive:$false
    if ($hardcoded) { throw "hard-coded secret? " + (($hardcoded | ForEach-Object { "$($_.Filename):$($_.LineNumber)" }) -join ', ') }
    'ok'
}

Check 'C7' 'Manifest hygiene' { Test-ManifestHygiene "$src/AndroidManifest.xml" 'com.freetia.tv' }

if (-not $Fast) {
    Check 'C11' 'Release build (R8) under 15 MB' {
        Gradle @('assembleFossRelease')
        $apk = Get-ChildItem app/build/outputs/apk/foss/release -Filter *.apk | Select-Object -First 1
        $mb = [math]::Round($apk.Length / 1MB, 1)
        if ($mb -ge 15) { throw "release APK is $mb MB" }
        "$mb MB"
    }
}

$failed = @($results.Keys | Where-Object { -not $results[$_].pass })
New-Item -ItemType Directory -Force build | Out-Null
[ordered]@{
    time = (Get-Date).ToString('o'); mode = $(if ($Fast) { 'fast' } else { 'full' })
    pass = ($failed.Count -eq 0); checks = $results
} | ConvertTo-Json -Depth 5 | Set-Content build/quality-gate.json -Encoding utf8

if ($failed.Count -gt 0) { Write-Host "`nGate FAILED: $($failed -join ', ')" -ForegroundColor Red; exit 1 }
Write-Host "`nGate PASSED ($($results.Count) checks). Results: build/quality-gate.json" -ForegroundColor Green
