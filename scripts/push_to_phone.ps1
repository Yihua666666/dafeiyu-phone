param(
    [string]$Serial = "",
    [string]$Adb = "adb",
    [string]$Apk = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Apk) { $Apk = Join-Path $root "dist\DafeiyuChat-1.0.0.apk" }

function Invoke-Adb {
    param([string[]]$Arguments)
    if ($Serial) { & $Adb -s $Serial @Arguments } else { & $Adb @Arguments }
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}

Write-Host "1/3 push AutoX scripts"
Invoke-Adb @("shell", "mkdir", "-p", "/sdcard/PhoneAgent")
foreach ($name in @("agent.js", "chat.js", "setup_boot.js")) {
    Invoke-Adb @("push", (Join-Path $root "autox\$name"), "/sdcard/PhoneAgent/$name")
}

Write-Host "2/3 push APK"
Invoke-Adb @("push", $Apk, "/sdcard/Download/DafeiyuChat.apk")

Write-Host "3/3 install APK (may require tapping Install on MIUI)"
& $Adb $(if ($Serial) { @("-s", $Serial) } else { @() }) install -r $Apk

Write-Host ""
Write-Host "Done."
Write-Host "Open AutoX.js and run /sdcard/PhoneAgent/agent.js once."
Write-Host "Fill the API key, then copy /sdcard/DeepSeekGirl/remote/topic.txt into the app's gear settings."
