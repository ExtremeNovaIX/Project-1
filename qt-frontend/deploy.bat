@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

for %%I in ("%SCRIPT_DIR%\build\qt-frontend") do set "BUILD_DIR=%%~fI"
for %%I in ("%BUILD_DIR%\arklight_qt.exe") do set "EXE_PATH=%%~fI"

set "WINDEPLOYQT="

if defined WINDEPLOYQT_EXE (
    set "WINDEPLOYQT=%WINDEPLOYQT_EXE%"
)

if not defined WINDEPLOYQT if defined QTDIR if exist "%QTDIR%\bin\windeployqt.exe" set "WINDEPLOYQT=%QTDIR%\bin\windeployqt.exe"
if not defined WINDEPLOYQT if defined QT_ROOT if exist "%QT_ROOT%\bin\windeployqt.exe" set "WINDEPLOYQT=%QT_ROOT%\bin\windeployqt.exe"

if not defined WINDEPLOYQT if exist "E:\QT\6.11.0\mingw_64\bin\windeployqt.exe" set "WINDEPLOYQT=E:\QT\6.11.0\mingw_64\bin\windeployqt.exe"
if not defined WINDEPLOYQT if exist "E:\QT\6.11.0\msvc2022_64\bin\windeployqt.exe" set "WINDEPLOYQT=E:\QT\6.11.0\msvc2022_64\bin\windeployqt.exe"
if not defined WINDEPLOYQT if exist "C:\Qt\6.7.3\mingw_64\bin\windeployqt.exe" set "WINDEPLOYQT=C:\Qt\6.7.3\mingw_64\bin\windeployqt.exe"
if not defined WINDEPLOYQT if exist "C:\Qt\6.7.3\msvc2022_64\bin\windeployqt.exe" set "WINDEPLOYQT=C:\Qt\6.7.3\msvc2022_64\bin\windeployqt.exe"

if not defined WINDEPLOYQT (
    echo Could not find windeployqt.exe.
    echo Set WINDEPLOYQT_EXE or QTDIR and try again.
    exit /b 1
)

if not exist "%EXE_PATH%" (
    echo Executable not found: %EXE_PATH%
    echo Run qt-frontend\build.bat first.
    exit /b 1
)

echo Deploying Qt runtime with:
echo   %WINDEPLOYQT%
echo To:
echo   %BUILD_DIR%

call "%WINDEPLOYQT%" --qmldir "%SCRIPT_DIR%\qml" --dir "%BUILD_DIR%" "%EXE_PATH%"
if errorlevel 1 exit /b 1

echo Deployment completed.
