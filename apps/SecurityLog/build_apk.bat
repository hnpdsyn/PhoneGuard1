@echo off
chcp 65001 >nul
title SecurityLog APK Build

echo ================================
echo   SecurityLog (安全日志) APK Builder
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

rem ===== Auto-detect Android SDK and create local.properties =====
if not exist "local.properties" (
    echo Detecting Android SDK location...
    powershell -NoProfile -Command "$found=''; foreach($p in @(($env:LOCALAPPDATA+'\Android\Sdk'), $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)){ if($p){ $t=$p.TrimEnd('\'); if(Test-Path ($t+'\platform-tools')){ $found=$t; break } } }; if($found){ [IO.File]::WriteAllText((Join-Path (Get-Location).Path 'local.properties'), ('sdk.dir='+($found -replace '\\','/'))); Write-Host ('[OK] SDK found: '+$found) } else { Write-Host '[ERROR] Android SDK not found on this PC.'; exit 1 }"
    if errorlevel 1 (
        echo.
        echo [Hint] Install Android Studio first, or create local.properties
        echo        in this folder manually with one line like:
        echo        sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk
        echo.
        pause
        exit /b 1
    )
    echo.
)

echo Building APK (assembleDebug)...
echo First build may take 5-15 minutes to download dependencies.
echo.

call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================
    echo   BUILD SUCCESS!
    echo ================================
    echo APK: app/build/outputs/apk/debug/app-debug.apk
    echo.
    pause
) else (
    echo.
    echo [Error] Build failed. Check the error messages above.
    echo.
    pause
)
