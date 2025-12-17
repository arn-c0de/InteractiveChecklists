
@echo off
REM DataPad Python Tool - Start GUI with Live Data
REM GUI automatically starts ECDH receiver based on datapad_config.json
setlocal
set VENV_DIR=.venv
set MAIN_SCRIPT=run_datapad.py

REM Change to script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Check for venv
if not exist "%VENV_DIR%\Scripts\python.exe" (
    echo venv not found. Please run install_venv.bat first.
    pause
    exit /b 1
)

echo ========================================
echo DataPad GUI with Live DCS Data
echo ========================================
echo Configuration loaded from datapad_config.json
echo Edit settings in GUI: Menu -^> Settings
echo ========================================
echo.

REM Start the GUI
"%VENV_DIR%\Scripts\python.exe" "%MAIN_SCRIPT%"
set rc=%errorlevel%

if %rc% neq 0 (
    echo Application exited with error code %rc%.
) else (
    echo Application exited normally.
)

pause
endlocal
exit /b %rc%
