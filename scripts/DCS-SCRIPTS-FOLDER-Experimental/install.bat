@echo off
REM =====================================================================
REM Installation Script - Sets up virtual environment and dependencies
REM =====================================================================

echo.
echo ========================================
echo  DCS DataPad Server - Installation
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
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Installation completed successfully!
echo ========================================
echo.
echo You can now run the server with: run.bat
echo.
pause
