@echo off
rem Shared environment for all release scripts.

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fI"

set "APP_CONFIG=%APP_HOME%\config\application.yml"
set "LOG_DIR=%APP_HOME%\logs"
set "OUTPUT_DIR=%APP_HOME%\output"
set "BASELINE_DIR=%APP_HOME%\baseline"
set "TOOLS_DIR=%APP_HOME%\tools"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
if not exist "%BASELINE_DIR%" mkdir "%BASELINE_DIR%"

where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java was not found in PATH. JDK 17 is required.
  exit /b 10
)

set "APP_JAR="
for %%J in ("%APP_HOME%\sql-postgres-validator-*.jar") do (
  if exist "%%~fJ" set "APP_JAR=%%~fJ"
)

if not defined APP_JAR (
  echo ERROR: Validator JAR was not found in "%APP_HOME%".
  exit /b 11
)

if not exist "%APP_CONFIG%" (
  echo ERROR: Configuration file was not found:
  echo        "%APP_CONFIG%"
  exit /b 12
)

exit /b 0
