param(
  [Parameter(Mandatory=$true)][string]$Base,
  [Parameter(Mandatory=$true)][string]$SupabaseUrl,
  [Parameter(Mandatory=$true)][string]$SupabaseKey,
  [string]$Apk = "..\app-debug.apk"
)
$ErrorActionPreference = "Stop"
$Base = $Base.TrimEnd('/')
$apkPath = Resolve-Path $Apk
$sha = [Security.Cryptography.SHA256]::Create().ComputeHash([IO.File]::ReadAllBytes($apkPath))
$sum = [Convert]::ToBase64String($sha).Replace('+','-').Replace('/','_').TrimEnd('=')

$cfg = [ordered]@{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" = "com.aether.convert/.admin.AdminReceiver"
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME" = "com.aether.convert"
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" = "$Base/app-debug.apk"
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" = $sum
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE" = @{ url = $SupabaseUrl; key = $SupabaseKey }
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION" = $true
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" = $true
}
$json = $cfg | ConvertTo-Json -Depth 5 -Compress
$json | Set-Content -NoNewline -Encoding ascii (Join-Path $PSScriptRoot "payload.json")

$esc = $json.Replace('\','\\').Replace('`','\`')
@"
<!doctype html><html><head><meta charset="utf-8"><title>Provision</title>
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<style>body{background:#0e0e12;color:#eee;font:14px system-ui;display:grid;place-items:center;height:100vh;margin:0}
#q{background:#fff;padding:20px;border-radius:16px}p{max-width:420px;text-align:center;color:#8a8a94;line-height:1.6}</style>
</head><body><div><div id="q"></div>
<p>Factory reset the phone. At the welcome screen tap the same spot 6 times to open the QR scanner, then scan this.</p></div>
<script>new QRCode(document.getElementById("q"),{text:`$esc`,width:620,height:620,correctLevel:QRCode.CorrectLevel.L});</script>
</body></html>
"@ | Set-Content -Encoding ascii (Join-Path $PSScriptRoot "qr.html")

Write-Host "qr.html written. Open it in a browser." -ForegroundColor Green
Write-Host "Checksum: $sum" -ForegroundColor DarkGray