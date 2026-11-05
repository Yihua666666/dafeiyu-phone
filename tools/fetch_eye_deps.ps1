$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$eye = Join-Path $root "app\DafeiyuEye"
$libs = Join-Path $eye "libs"
$raw = Join-Path $eye "res\raw"
$cache = Join-Path $root ".eye-deps"

New-Item -ItemType Directory -Force -Path $libs, $raw, $cache | Out-Null

$aar = Join-Path $cache "sherpa-onnx.aar"
$kotlin = Join-Path $cache "kotlin-stdlib.jar"
$modelTar = Join-Path $cache "paraformer-zh-small.tar.bz2"

if (-not (Test-Path $aar)) {
    Invoke-WebRequest -Uri "https://jitpack.io/com/github/k2-fsa/sherpa-onnx/sherpa-onnx/v1.13.6/sherpa-onnx-v1.13.6.aar" -OutFile $aar
}
if (-not (Test-Path $kotlin)) {
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar" -OutFile $kotlin
}
if (-not (Test-Path $modelTar)) {
    Invoke-WebRequest -Uri "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2" -OutFile $modelTar
}

$aarDir = Join-Path $cache "aar"
if (-not (Test-Path $aarDir)) {
    $zip = Join-Path $cache "sherpa-onnx.zip"
    Copy-Item $aar $zip -Force
    Expand-Archive -Path $zip -DestinationPath $aarDir -Force
}
Copy-Item (Join-Path $aarDir "classes.jar") (Join-Path $libs "sherpa-onnx-classes.jar") -Force
Copy-Item $kotlin (Join-Path $libs "kotlin-stdlib.jar") -Force
New-Item -ItemType Directory -Force -Path (Join-Path $libs "arm64-v8a") | Out-Null
Copy-Item (Join-Path $aarDir "jni\arm64-v8a\*.so") (Join-Path $libs "arm64-v8a") -Force

$modelDir = Join-Path $cache "model"
if (-not (Test-Path $modelDir)) {
    New-Item -ItemType Directory -Force -Path $modelDir | Out-Null
    tar -xjf $modelTar -C $modelDir
}
$src = Get-ChildItem $modelDir -Recurse -Filter "model.int8.onnx" | Select-Object -First 1
$tok = Get-ChildItem $modelDir -Recurse -Filter "tokens.txt" | Select-Object -First 1
if (-not $src -or -not $tok) { throw "model files not found" }
Copy-Item $src.FullName (Join-Path $raw "asr_model.onnx") -Force
Copy-Item $tok.FullName (Join-Path $raw "asr_tokens.txt") -Force

Write-Host "DafeiyuEye deps ready:"
Get-ChildItem $libs -Recurse -File | Select-Object FullName, Length
Get-ChildItem $raw -File | Select-Object FullName, Length
