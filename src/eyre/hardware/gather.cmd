@echo off
echo ===cpu===
wmic cpu get Name, NumberOfCores, Architecture /value 2>nul
echo PROCESSOR_ARCHITECTURE=%PROCESSOR_ARCHITECTURE%
echo ===memory===
wmic computersystem get TotalPhysicalMemory /value 2>nul
wmic pagefile get AllocatedBaseSize /value 2>nul
echo ===disks===
wmic diskdrive get DeviceID, Model, Size, InterfaceType /value 2>nul
echo ===virtualization===
wmic computersystem get Manufacturer, Model /value 2>nul
echo ===end===
