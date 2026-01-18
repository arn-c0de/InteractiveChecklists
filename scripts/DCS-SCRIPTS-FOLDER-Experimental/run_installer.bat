@echo off
REM =====================================================================
REM DCS DataPad Server - GUI Installer Launcher (with venv)
REM =====================================================================

echo.
echo ========================================
echo  DCS DataPad Server - GUI Installer
echo ========================================
echo.

REM Check if Python is available
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.8 or newer from https://www.python.org/
    pause
    exit /b 1
)

echo [1/3] Checking virtual environment...
if not exist "venv" (
    echo Creating new virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo ERROR: Failed to create virtual environment
        pause
        exit /b 1
    )
    echo Virtual environment created successfully!
) else (
    echo Virtual environment already exists
)

echo.
echo [2/3] Activating virtual environment...
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo ERROR: Failed to activate virtual environment
    pause
    exit /b 1
)

echo.
echo [3/3] Installing/updating dependencies...
python -m pip install --upgrade pip >nul
python -m pip install -r requirements_gui.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Starting GUI Installer...
echo ========================================
echo.

REM Launch GUI installer
python dcs_datapad_installer.py

if errorlevel 1 (
    echo.
    echo Application exited with error
    pause
    exit /b 1
)

REM Deactivate virtual environment
call venv\Scripts\deactivate.bat
