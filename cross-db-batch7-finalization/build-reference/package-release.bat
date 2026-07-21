@echo off
setlocal EnableExtensions

if "%~4"=="" (
  echo Usage:
  echo package-release.bat BUILT_JAR VERSION TEMPLATE_DIR OUTPUT_DIR [APPROVED_BASELINE]
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass ^
  -File "%~dp0package-release.ps1" ^
  -BuiltJar "%~1" ^
  -Version "%~2" ^
  -TemplateDirectory "%~3" ^
  -OutputDirectory "%~4" ^
  -ApprovedBaseline "%~5"

exit /b %ERRORLEVEL%
