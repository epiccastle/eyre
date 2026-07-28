Write-Output '===ver==='
cmd /c ver
Write-Output '===osinfo==='
$os = Get-CimInstance Win32_OperatingSystem
"Caption=$($os.Caption)"
"Version=$($os.Version)"
"BuildNumber=$($os.BuildNumber)"
"OSArchitecture=$($os.OSArchitecture)"
Write-Output '===arch==='
Write-Output $env:PROCESSOR_ARCHITECTURE
$LASTEXITCODE = 0
