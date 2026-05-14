# run.ps1 — Kuran Analiz Merkezi baslatici
# gradle-wrapper.jar yerine onceden indirilmis Gradle'i kullanir

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$GradleArgs = @(":composeApp:run")
)

$projectDir = "C:\KP"

# Onceden indirilmis Gradle 8.9 binary'sini bul
$gradleBin = (Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.9-bin" -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName

if (-not $gradleBin) {
    # Alternatif: gradle-8.7 veya baska surum
    $gradleBin = (Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}

if (-not $gradleBin) {
    Write-Error "Gradle bulunamadi. Lutfen projeyi IDE uzerinden calistirin."
    Read-Host "Cikis icin Enter'a basin"
    exit 1
}

Write-Host "Gradle: $gradleBin" -ForegroundColor Cyan
Write-Host "Gorev : $GradleArgs" -ForegroundColor Yellow
Write-Host ""

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

& $gradleBin "--project-dir" $projectDir @GradleArgs

exit $LASTEXITCODE
