Write-Output '===volumes==='
$disks = Get-CimInstance Win32_LogicalDisk -ErrorAction SilentlyContinue
if ($null -eq $disks) { $disks = Get-WmiObject Win32_LogicalDisk -ErrorAction SilentlyContinue }
if ($disks) {
  $disks | ForEach-Object {
    "$($_.DeviceID)|$($_.FileSystem)|$($_.Size)|$($_.FreeSpace)|$($_.DriveType)"
  }
}
Write-Output '===features==='
try {
  $bl = Get-CimInstance -Namespace "root\cimv2\security\MicrosoftVolumeEncryption" -ClassName Win32_EncryptableVolume -ErrorAction Stop
  $bl | ForEach-Object {
    "$($_.DriveLetter)|$($_.ProtectionStatus)"
  }
} catch {}
Write-Output '===end==='
