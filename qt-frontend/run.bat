@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
for %%I in ("%SCRIPT_DIR%\build\qt-frontend\Debug\arklight_qt.exe") do set "EXE_PATH=%%~fI"

if not exist "%EXE_PATH%" (
    for %%I in ("%SCRIPT_DIR%\build\qt-frontend\arklight_qt.exe") do set "EXE_PATH=%%~fI"
)

for %%I in ("%SCRIPT_DIR%\build\qt-frontend\Qt6Gui.dll") do set "QT_GUI_DLL=%%~fI"

if not exist "%EXE_PATH%" (
    echo Executable not found: %EXE_PATH%
    echo Run qt-frontend\build.bat first.
    exit /b 1
)

if not exist "%QT_GUI_DLL%" (
    echo Qt runtime was not deployed. Running deploy.bat first...
    call "%SCRIPT_DIR%\deploy.bat"
    if errorlevel 1 exit /b 1
)

pushd "%SCRIPT_DIR%.."
"%EXE_PATH%"
set "EXIT_CODE=%ERRORLEVEL%"
popd

exit /b %EXIT_CODE%
