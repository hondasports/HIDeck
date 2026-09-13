[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)] [string]$Serial,
    [Parameter(Mandatory)] [string]$PatchedBoot,
    [Parameter(Mandatory)] [string]$OriginalBoot,
    [string]$AuditDirectory = (Join-Path (Get-Location) "work\pixel3-audit"),
    [switch]$ConfirmFlash
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $PatchedBoot -PathType Leaf)) { throw "Patched boot image not found" }
if (-not (Test-Path -LiteralPath $OriginalBoot -PathType Leaf)) { throw "Original matching boot image not found" }
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw "adb is not on PATH" }
if (-not (Get-Command fastboot -ErrorAction SilentlyContinue)) { throw "fastboot is not on PATH" }

if ((adb -s $Serial get-state).Trim() -ne "device") { throw "ADB is not authorized for $Serial" }
$product = (adb -s $Serial shell getprop ro.product.device).Trim()
if ($product -ne "blueline") { throw "Expected Pixel 3 blueline, got '$product'" }
$slot = (adb -s $Serial shell getprop ro.boot.slot_suffix).Trim()
if ($slot -notin @("_a", "_b")) { throw "Could not determine active A/B slot" }
$lineage = (adb -s $Serial shell getprop ro.lineage.version).Trim()
$incremental = (adb -s $Serial shell getprop ro.build.version.incremental).Trim()
Write-Host "Target: blueline  LineageOS=$lineage  incremental=$incremental  slot=$slot"

$patchHash = (Get-FileHash -LiteralPath $PatchedBoot -Algorithm SHA256).Hash
$originalHash = (Get-FileHash -LiteralPath $OriginalBoot -Algorithm SHA256).Hash
Write-Host "patched sha256=$patchHash"
Write-Host "original sha256=$originalHash"

if (-not $ConfirmFlash) {
    Write-Host "No flash performed. Review the values above, then rerun with -ConfirmFlash."
    exit 0
}

adb -s $Serial reboot bootloader
$fastbootSerial = $null
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 2
    $fastbootLines = @(fastboot devices | Where-Object { $_ -match "\S+" })
    if ($fastbootLines.Count -gt 0) { $fastbootSerial = ($fastbootLines[0].ToString() -split "\s+")[0] }
} while (-not $fastbootSerial -and (Get-Date) -lt $deadline)
if (-not $fastbootSerial) { throw "No fastboot device found" }
$productLine = (cmd /c "fastboot -s $fastbootSerial getvar product 2>&1" | Out-String)
if ($productLine -notmatch "blueline") { throw "Fastboot product is not blueline`n$productLine" }
$unlockLine = (cmd /c "fastboot -s $fastbootSerial getvar unlocked 2>&1" | Out-String)
if ($unlockLine -notmatch "unlocked: yes") { throw "Bootloader is not unlocked`n$unlockLine" }
$fastSlotLine = (cmd /c "fastboot -s $fastbootSerial getvar current-slot 2>&1" | Out-String)
if ($fastSlotLine -notmatch "current-slot: (a|b)") { throw "Could not read fastboot current slot`n$fastSlotLine" }
$fastSlot = [regex]::Match($fastSlotLine, "current-slot: (a|b)").Groups[1].Value
if ($fastSlot -ne $slot.TrimStart("_")) { throw "Slot changed from $slot to $fastSlot; aborting" }

$backup = Join-Path $AuditDirectory "original-boot-$fastSlot.img"
Copy-Item -LiteralPath $OriginalBoot -Destination $backup -Force
if ($PSCmdlet.ShouldProcess("Pixel 3 slot $fastSlot", "flash matching Magisk-patched boot image")) {
    fastboot -s $fastbootSerial flash boot $PatchedBoot
    fastboot -s $fastbootSerial reboot
    Write-Host "Flashed and rebooted. Wait for Android, then run: adb -s $Serial shell su -c id"
}
