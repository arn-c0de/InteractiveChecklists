@echo off
REM =====================================================================
REM Run Script - Starts DCS DataPad Server with configuration menu
REM =====================================================================

echo.
echo ========================================
echo  DCS DataPad Server - Launcher
echo ========================================
echo.

REM Check if virtual environment exists
if not exist "venv" (
    echo ERROR: Virtual environment not found!
    echo Please run install.bat first to set up the environment.
    echo.
    pause
    exit /b 1
)

REM Activate virtual environment
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo ERROR: Failed to activate virtual environment
    pause
    exit /b 1
)

REM Check if forward_parsed_udp.py exists
if not exist "forward_parsed_udp.py" (
    echo ERROR: forward_parsed_udp.py not found!
    echo Make sure you are running this script from the correct directory.
    pause
    exit /b 1
)

REM Start configuration menu
python config.py
if errorlevel 1 (
    echo.
    echo Server exited with error
    pause
    exit /b 1
)

REM Deactivate virtual environment
call venv\Scripts\deactivate.bat
