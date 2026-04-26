# Find adb executable
$candidates = @(
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$adb = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
}
if (-not $adb) {
    Write-Error "adb not found. Set ANDROID_HOME or add platform-tools to PATH."
    exit 1
}

# Get connected devices
$deviceLines = (& $adb devices | Select-Object -Skip 1) | Where-Object { $_ -match "`tdevice$" }
$deviceCount = @($deviceLines).Count

if ($deviceCount -eq 0) {
    Write-Error "No devices connected. Start an emulator or connect a real device."
    exit 1
}

$adbArgs = @()
if ($deviceCount -gt 1) {
    # Prefer real device over emulator
    $serial = ($deviceLines | Where-Object { $_ -notmatch "^emulator-" } | Select-Object -First 1) -replace "`t.*", ""
    if (-not $serial) {
        $serial = ($deviceLines | Select-Object -First 1) -replace "`t.*", ""
    }
    Write-Host "Multiple devices found, using: $serial"
    $adbArgs = @("-s", $serial)
}

Write-Host "Building and installing..."
.\gradlew :composeApp:installDebug

Write-Host "Launching app..."
& $adb @adbArgs shell am start -n com.rectime.mobile/.MainActivity
