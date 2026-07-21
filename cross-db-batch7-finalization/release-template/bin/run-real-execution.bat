@echo off
setlocal EnableExtensions

call "%~dp0common.bat"
if errorlevel 1 exit /b %errorlevel%

echo ===============================================================
echo WARNING: REAL EXECUTION MAY CHANGE DATABASE DATA.
echo Existing include/exclude whitelist protection must remain enabled.
echo Use only in an approved environment.
echo ===============================================================
set /p "CONFIRM=Type RUN-REAL-EXECUTION to continue: "

if /I not "%CONFIRM%"=="RUN-REAL-EXECUTION" (
  echo Cancelled.
  exit /b 30
)

pushd "%APP_HOME%"
java -jar "%APP_JAR%" ^
  --spring.config.additional-location="file:%APP_CONFIG%" ^
  --validator.phase=real-execution ^
  >> "%LOG_DIR%\real-execution.log" 2>&1
set "RC=%ERRORLEVEL%"
popd

exit /b %RC%
