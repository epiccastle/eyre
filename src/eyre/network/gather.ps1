Write-Output '===hostname==='
$env:COMPUTERNAME
Write-Output '===ipinterface==='
Get-NetIPInterface -ErrorAction SilentlyContinue | ForEach-Object {
  "$($_.InterfaceAlias)|$($_.InterfaceIndex)|$($_.AddressFamily)|$($_.ConnectionState)|$($_.NlMtu)"
}
Write-Output '===addresses==='
Get-NetIPAddress -ErrorAction SilentlyContinue | ForEach-Object {
  "$($_.InterfaceAlias)|$($_.IPAddress)|$($_.AddressFamily)|$($_.PrefixLength)"
}
Write-Output '===adapter==='
Get-NetAdapter -ErrorAction SilentlyContinue | ForEach-Object {
  "$($_.Name)|$($_.MacAddress)|$($_.Status)|$($_.MTU)"
}
Write-Output '===route==='
Get-NetRoute -DestinationPrefix 0.0.0.0/0 -ErrorAction SilentlyContinue | ForEach-Object {
  "$($_.InterfaceAlias)|$($_.NextHop)"
}
Write-Output '===dns==='
Get-DnsClientServerAddress -ErrorAction SilentlyContinue | ForEach-Object {
  "$($_.InterfaceAlias)|$($_.AddressFamily)|$($_.ServerAddresses -join ',')"
}
Write-Output '===end==='
