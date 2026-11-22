param([string]$Pkg = "com.aether.convert")
$ErrorActionPreference = "Stop"
$ops = @("android:background_location","RUN_IN_BACKGROUND","RUN_ANY_IN_BACKGROUND","WAKE_LOCK")
foreach ($o in $ops) { & adb shell appops set $Pkg $o allow 2>$null }
& adb shell dumpsys deviceidle whitelist +$Pkg
& adb shell am start -n "$Pkg/.MainActivity"
Write-Host "re-armed" -ForegroundColor Green