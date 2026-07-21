@echo off
setlocal EnableExtensions

call "%~dp0common.bat"
if errorlevel 1 exit /b %errorlevel%

pushd "%APP_HOME%"
echo Running phase: excel-report
java -jar "%APP_JAR%" ^
  --spring.config.additional-location="file:%APP_CONFIG%" ^
  --validator.phase=excel-report  ^
  >> "%LOG_DIR%\excel-report.log" 2>&1
set "RC=%ERRORLEVEL%"
popd

if not "%RC%"=="0" (
  echo Phase failed with exit code %RC%.
  echo See "%LOG_DIR%\excel-report.log"
) else (
  echo Phase completed successfully.
)

exit /b %RC%
