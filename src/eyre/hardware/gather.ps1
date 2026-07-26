Write-Output '===cpu==='
$proc = Get-CimInstance Win32_Processor -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $proc) { $proc = Get-WmiObject Win32_Processor -ErrorAction SilentlyContinue | Select-Object -First 1 }
$reg_cp = Get-ItemProperty -Path "HKLM:\HARDWARE\DESCRIPTION\System\CentralProcessor\0" -ErrorAction SilentlyContinue
$featureset = if ($reg_cp) { $reg_cp.FeatureSet } else { "" }
if ($proc) {
  "$($proc.Name)|$($proc.NumberOfCores)|$env:PROCESSOR_ARCHITECTURE|$($proc.Architecture)|$featureset"
} else {
  "|$env:NUMBER_OF_PROCESSORS|$env:PROCESSOR_ARCHITECTURE||"
}

Write-Output '===memory==='
$sys = Get-CimInstance Win32_ComputerSystem -ErrorAction SilentlyContinue
if ($null -eq $sys) { $sys = Get-WmiObject Win32_ComputerSystem -ErrorAction SilentlyContinue }
$total_mem = if ($sys) { $sys.TotalPhysicalMemory } else { 0 }
$pagefile = Get-CimInstance Win32_PageFileUsage -ErrorAction SilentlyContinue
if ($null -eq $pagefile) { $pagefile = Get-WmiObject Win32_PageFileUsage -ErrorAction SilentlyContinue }
$total_swap = 0
if ($pagefile) {
  foreach ($pf in $pagefile) { $total_swap += $pf.AllocatedBaseSize * 1024 * 1024 }
}
"$total_mem|$total_swap"

Write-Output '===disks==='
if (Get-Command Get-PhysicalDisk -ErrorAction SilentlyContinue) {
  Get-PhysicalDisk -ErrorAction SilentlyContinue | ForEach-Object {
    "$($_.DeviceId)|$($_.FriendlyName)|$($_.Size)|$($_.MediaType)"
  }
} else {
  $drives = Get-CimInstance Win32_DiskDrive -ErrorAction SilentlyContinue
  if ($null -eq $drives) { $drives = Get-WmiObject Win32_DiskDrive -ErrorAction SilentlyContinue }
  if ($drives) {
    $drives | ForEach-Object {
      "$($_.Index)|$($_.Caption)|$($_.Size)|"
    }
  }
}

Write-Output '===virtualization==='
if ($sys) {
  "$($sys.Manufacturer)|$($sys.Model)"
} else {
  "|"
}
