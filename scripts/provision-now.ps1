param(
  [string]$Pkg = "com.aether.convert",
  [string]$Apk = ".\app-debug.apk"
)
$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb not on PATH" }
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

& adb devices

& adb install -r -g $Apk
if ($LASTEXITCODE -ne 0) {
  throw "install failed - uninstall the old package first if signatures differ"
}

$perms = @(
  "ACCESS_FINE_LOCATION","ACCESS_COARSE_LOCATION","ACCESS_BACKGROUND_LOCATION",
  "ACTIVITY_RECOGNITION","POST_NOTIFICATIONS","RECEIVE_BOOT_COMPLETED","WAKE_LOCK"
)
foreach ($p in $perms) { & adb shell pm grant $Pkg "android.permission.$p" 2>$null }

$ops = @(
  "android:fine_location","android:coarse_location","android:background_location",
  "android:activity_recognition","RUN_IN_BACKGROUND","RUN_ANY_IN_BACKGROUND",
  "WAKE_LOCK","START_FOREGROUND"
)
foreach ($o in $ops) { & adb shell appops set $Pkg $o allow 2>$null }

& adb shell dumpsys deviceidle whitelist +$Pkg
& adb shell cmd deviceidle whitelist +$Pkg
& adb shell am set-standby-bucket $Pkg active

& adb shell am start -n "$Pkg/.MainActivity"
Start-Sleep -Seconds 2

Write-Host "`n===== VERIFY =====" -ForegroundColor Cyan
Write-Host "background location:"
& adb shell dumpsys package $Pkg | Select-String "ACCESS_BACKGROUND_LOCATION"
Write-Host "`ndoze whitelist:"
& adb shell dumpsys deviceidle whitelist | Select-String $Pkg
Write-Host "`nappops:"
& adb shell appops get $Pkg
Write-Host "`nnotification (expect none):"
& adb shell dumpsys notification --noredact | Select-String "com.aether.convert"