@echo off
REM DataPad Python Tool - Start GUI in venv
setlocal
set VENV_DIR=.venv
set MAIN_SCRIPT=datapad_gui.py

REM Change to script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Check for venv
if not exist "%VENV_DIR%\Scripts\python.exe" (
    echo venv not found. Please run install_datapad_venv.bat first.
    pause
    exit /b 1
)

REM Start the app
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
