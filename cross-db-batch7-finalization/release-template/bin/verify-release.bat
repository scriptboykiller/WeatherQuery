@echo off
setlocal EnableExtensions

call "%~dp0common.bat"
if errorlevel 1 exit /b %errorlevel%

if not exist "%APP_HOME%\SHA256SUMS.txt" (
  echo ERROR: SHA256SUMS.txt was not found.
  exit /b 40
)

where powershell >nul 2>&1
if errorlevel 1 (
  echo ERROR: PowerShell is required for checksum verification.
  exit /b 41
)

powershell -NoProfile -ExecutionPolicy Bypass ^
  -File "%~dp0verify-release.ps1" ^
  -ReleaseRoot "%APP_HOME%" ^
  -ChecksumFile "%APP_HOME%\SHA256SUMS.txt"

exit /b %ERRORLEVEL%
