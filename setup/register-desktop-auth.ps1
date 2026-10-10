[CmdletBinding(DefaultParameterSetName = 'Register')]
param(
    [Parameter(ParameterSetName = 'Register')][switch]$Register,
    [Parameter(Mandatory, ParameterSetName = 'Build')][switch]$Build,
    [Parameter(Mandatory, ParameterSetName = 'Unregister')][switch]$Unregister
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$taskRepoRoot = Split-Path $PSScriptRoot -Parent
$taskBuildDir = Join-Path $taskRepoRoot 'composeApp\build\desktop-auth'
$taskExecutable = Join-Path $taskBuildDir 'rectime-mobile-auth.exe'
$taskCommand = '"' + $taskExecutable + '" "%1"'
$taskProtocolKey = 'Software\Classes\com.rectime.mobile'

function Send-DesktopAuthAssociationChange {
    if (-not ('RectimeDesktopAuth.AssociationChange' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace RectimeDesktopAuth {
    public static class AssociationChange {
        [DllImport("shell32.dll")]
        public static extern void SHChangeNotify(int eventId, uint flags, IntPtr item1, IntPtr item2);
    }
}
'@
    }
    # 登録変更を通知し、Windowsの関連付け情報を更新する。
    [RectimeDesktopAuth.AssociationChange]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)
}

if ($Unregister) {
    $taskExistingCommand = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey($taskProtocolKey + '\shell\open\command')
    try {
        if ($null -ne $taskExistingCommand -and $taskExistingCommand.GetValue('') -ne $taskCommand) {
            throw '別のアプリの登録は削除しません。'
        }
    } finally {
        if ($null -ne $taskExistingCommand) { $taskExistingCommand.Dispose() }
    }
    [Microsoft.Win32.Registry]::CurrentUser.DeleteSubKeyTree($taskProtocolKey, $false)
    Send-DesktopAuthAssociationChange
    Write-Output 'Desktopの認証URL登録を解除しました。'
    exit
}

$taskCompiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $taskCompiler)) { throw '.NET FrameworkのC#コンパイラが見つかりません。' }
New-Item -ItemType Directory -Path $taskBuildDir -Force | Out-Null
& $taskCompiler /nologo /target:winexe /codepage:65001 /optimize+ "/out:$taskExecutable" (Join-Path $PSScriptRoot 'DesktopAuthProtocol.cs')
if ($LASTEXITCODE -ne 0) { throw '認証URL受信用プログラムのビルドに失敗しました。' }
if ($Build) { Write-Output 'Desktopの認証URL受信用プログラムをビルドしました。'; exit }

$taskExistingCommand = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey($taskProtocolKey + '\shell\open\command')
try {
    if ($null -ne $taskExistingCommand -and $taskExistingCommand.GetValue('') -ne $taskCommand) {
        throw 'この認証URLは別のアプリに登録済みです。'
    }
} finally {
    if ($null -ne $taskExistingCommand) { $taskExistingCommand.Dispose() }
}

$taskKey = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey($taskProtocolKey)
try {
    $taskKey.SetValue('', 'URL:rectime-mobile')
    $taskKey.SetValue('URL Protocol', '')
    $taskKey.SetValue('FriendlyAppName', 'rectime-mobile')
    $taskApplicationKey = $taskKey.CreateSubKey('Application')
    try { $taskApplicationKey.SetValue('ApplicationName', 'rectime-mobile') } finally { $taskApplicationKey.Dispose() }
    $taskCommandKey = $taskKey.CreateSubKey('shell\open\command')
    try { $taskCommandKey.SetValue('', $taskCommand) } finally { $taskCommandKey.Dispose() }
} finally {
    $taskKey.Dispose()
}
Send-DesktopAuthAssociationChange
Write-Output 'このWindowsユーザーにDesktopの認証URLを登録しました。'
