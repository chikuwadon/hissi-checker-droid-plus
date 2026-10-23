# Requires adb on PATH; read-only unless -InstallCandidate is passed.
param([string]$Serial = "",[string]$ApkPath = "",[switch]$InstallCandidate,[string]$OutputDir = ".\real-device-evidence")
$ErrorActionPreference = "Stop"
function RunAdb([string[]]$ArgumentList) {
  $args2=@()
  if ($Serial) { $args2+=@("-s",$Serial) }
  $args2+=$ArgumentList
  $result = & adb @args2 2>&1
  if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($ArgumentList -join ' ')" }
  return ($result -join [Environment]::NewLine)
}
$state=RunAdb @("get-state")
if ($state.Trim() -ne "device") { throw "Specify an authorized device using -Serial if needed" }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$pkg="com.chikuwadon.hissidroidplus"
if ($InstallCandidate) {
  if (-not $ApkPath -or -not (Test-Path -LiteralPath $ApkPath)) { throw "Use -ApkPath with -InstallCandidate" }
  $installed=RunAdb @("install","-r",$ApkPath)
  if ($installed -notmatch "Success") { throw "Installation did not complete" }
}
$api=(RunAdb @("shell","getprop","ro.build.version.sdk")).Trim()
$details=RunAdb @("shell","dumpsys","package",$pkg)
if ($details -notmatch "versionCode=") { throw "App not installed" }
$version=($details -split "\r?\n" | Where-Object { $_ -match "versionCode=" } | Select-Object -First 1).Trim()
$launch=RunAdb @("shell","am","start","-W","-n","$pkg/.HomeActivity")
$mem=RunAdb @("shell","dumpsys","meminfo",$pkg)
$time=($launch -split "\r?\n" | Where-Object { $_ -match "TotalTime:" } | Select-Object -First 1)
$total=($mem -split "\r?\n" | Where-Object { $_ -match "^\s*TOTAL\s+\d+" } | Select-Object -First 1)
$data=[ordered]@{checked=(Get-Date).ToString("o");api=$api;package=$pkg;version=$version;startup=$time;memory=$total;scope="local metrics only"}
$data | ConvertTo-Json | Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir "device-summary.json")
Write-Host ("Saved: " + (Join-Path $OutputDir "device-summary.json"))
Write-Host "See docs/REAL_DEVICE_ACCEPTANCE.md for manual checks."
