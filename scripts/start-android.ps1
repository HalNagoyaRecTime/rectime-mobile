# Find emulator executable
$candidates = @(
    "$env:ANDROID_HOME\emulator\emulator.exe",
    "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
)

$emulator = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $emulator) {
    $cmd = Get-Command emulator -ErrorAction SilentlyContinue
    if ($cmd) { $emulator = $cmd.Source }
}
if (-not $emulator) {
    Write-Error "emulator not found. Set ANDROID_HOME or add emulator to PATH."
    exit 1
}

# Auto-detect first available AVD
$avd = (& $emulator -list-avds 2>$null) | Select-Object -First 1
if (-not $avd) {
    Write-Error "No AVDs found. Create one in Android Studio."
    exit 1
}

Write-Host "Starting AVD: $avd"
& $emulator -avd $avd
