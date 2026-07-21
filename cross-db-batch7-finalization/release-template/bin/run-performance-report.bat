@echo off
setlocal EnableExtensions

call "%~dp0common.bat"
if errorlevel 1 exit /b %errorlevel%

set "PERFORMANCE_JAR=%TOOLS_DIR%\sql-performance-comparator.jar"

if not exist "%PERFORMANCE_JAR%" (
  echo OPTIONAL TOOL NOT INSTALLED:
  echo "%PERFORMANCE_JAR%"
  echo.
  echo The core Validator and Excel report can still be used.
  echo Add the comparator JAR later, configure it, then run again.
  exit /b 20
)

pushd "%APP_HOME%"
java -jar "%APP_JAR%" ^
  --spring.config.additional-location="file:%APP_CONFIG%" ^
  --validator.phase=performance-report ^
  --validator.performance-report.enabled=true ^
  >> "%LOG_DIR%\performance-report.log" 2>&1
set "RC=%ERRORLEVEL%"
popd

if not "%RC%"=="0" (
  echo Optional performance phase failed with exit code %RC%.
  echo Core validation results are unchanged.
  echo See "%LOG_DIR%\performance-report.log"
) else (
  echo Performance phase completed successfully.
)

exit /b %RC%
