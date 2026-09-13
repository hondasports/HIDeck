[CmdletBinding()]
param(
    [string]$Serial,
    [string]$OutputDirectory = (Join-Path (Get-Location) "work\pixel3-audit")
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on PATH" }
if (-not $Serial) {
    $devices = @(adb devices | Select-String "\tdevice$")
    if ($devices.Count -ne 1) { throw "Pass -Serial because zero or multiple authorized devices are connected" }
    $Serial = ($devices[0].ToString() -split "\s+")[0]
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$state = (adb -s $Serial get-state).Trim()
if ($state -ne "device") { throw "ADB state for $Serial is '$state'; unlock the phone and accept the RSA prompt" }

$props = adb -s $Serial shell getprop
$props | Set-Content -LiteralPath (Join-Path $OutputDirectory "getprop.txt") -Encoding UTF8
function Get-Prop([string]$name) {
    $line = $props | Select-String "\[$([regex]::Escape($name))\]:"
    if ($line) { return (($line.ToString() -split "]: \[", 2)[1]).TrimEnd("]") }
    return ""
}

$product = Get-Prop "ro.product.device"
$lineage = Get-Prop "ro.lineage.version"
$incremental = Get-Prop "ro.build.version.incremental"
$slot = (adb -s $Serial shell getprop ro.boot.slot_suffix).Trim()
$verified = (adb -s $Serial shell getprop ro.boot.verifiedbootstate).Trim()
$root = ((cmd /c "adb -s $Serial shell su -c id 2>&1" | Out-String).Trim())

if ($product -and $product -ne "blueline") { throw "Connected device is '$product', expected Pixel 3 blueline" }
@"
HIDeck Pixel 3 audit
timestamp=$(Get-Date -Format o)
serial=$Serial
product=$product
lineage=$lineage
incremental=$incremental
slot_suffix=$slot
verified_boot_state=$verified
root_probe=$root
"@ | Set-Content -LiteralPath (Join-Path $OutputDirectory "summary.txt") -Encoding UTF8

adb -s $Serial shell getprop ro.boot.slot_suffix | Set-Content (Join-Path $OutputDirectory "active-slot.txt")
Write-Host "Audit saved to $OutputDirectory"
Write-Host "product=$product lineage=$lineage incremental=$incremental slot=$slot verified=$verified"
