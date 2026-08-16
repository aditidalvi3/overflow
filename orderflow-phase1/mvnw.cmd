@echo off
setlocal EnableDelayedExpansion
rem Maven Wrapper: downloads and caches the Apache Maven distribution pinned in
rem .mvn\wrapper\maven-wrapper.properties, then delegates to it - no separate Maven
rem install required. To change the Maven version, edit that properties file, not this script.

set "WRAPPER_DIR=%~dp0"
set "PROPERTIES_FILE=%WRAPPER_DIR%.mvn\wrapper\maven-wrapper.properties"

set "DISTRIBUTION_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES_FILE%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
)

for %%F in ("%DISTRIBUTION_URL%") do set "DIST_ZIP_NAME=%%~nxF"
set "MAVEN_DIR_NAME=%DIST_ZIP_NAME:-bin.zip=%"

if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2\wrapper"
set "DIST_DIR=%MAVEN_USER_HOME%\dists\%MAVEN_DIR_NAME%"
set "MAVEN_HOME=%DIST_DIR%\%MAVEN_DIR_NAME%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo mvnw: downloading Maven from %DISTRIBUTION_URL% ...
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%DIST_DIR%\%DIST_ZIP_NAME%'; Expand-Archive -Path '%DIST_DIR%\%DIST_ZIP_NAME%' -DestinationPath '%DIST_DIR%' -Force; Remove-Item '%DIST_DIR%\%DIST_ZIP_NAME%'"
  if errorlevel 1 (
    echo mvnw: failed to download or extract Maven.
    exit /b 1
  )
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
