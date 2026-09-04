@echo off
chcp 65001 >nul
title PhoneGuard APK Build

echo ================================
echo   PhoneGuard APK Builder
echo ================================
echo.

cd /d "%~dp0"

echo Checking Java...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [Error] Java not found. Please install JDK 17 first.
    pause
    exit /b 1
)
echo Java OK
echo.

echo Building APK (assembleDebug)...
echo First build may take 5-15 minutes to download dependencies.
echo.

call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================
    echo   BUILD SUCCESS!
    echo ================================
    echo APK: appuild\outputspk\debugpp-debug.apk
    echo.
    pause
) else (
    echo.
    echo [Error] Build failed. Check the error messages above.
    echo.
    pause
)
