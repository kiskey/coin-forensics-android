@echo off
setlocal
set VERSION=9.6.0
set BASE=%USERPROFILE%\.gradle\coinforensics-bootstrap
set HOME_DIR=%BASE%\gradle-%VERSION%
set ZIP=%BASE%\gradle-%VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip

if not exist "%HOME_DIR%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  echo Gradle %VERSION% not found locally; downloading official distribution...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ZIP%'; Expand-Archive -Force '%ZIP%' '%BASE%'"
  if errorlevel 1 exit /b 1
)
call "%HOME_DIR%\bin\gradle.bat" %*
endlocal
