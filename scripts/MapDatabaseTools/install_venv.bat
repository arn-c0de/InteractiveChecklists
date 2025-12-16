@echo off
REM DataPad Python Tool - Install dependencies into venv
setlocal
set VENV_DIR=.venv
set REQUIREMENTS=requirements.txt

REM Change to script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Python-Launcher finden
where py >nul 2>&1
if errorlevel 1 (
    where python >nul 2>&1
    if errorlevel 1 (
        where python3 >nul 2>&1
        if errorlevel 1 (
            echo No Python found. Please install Python 3.
            pause
            exit /b 1
        ) else (
            set "PYEXEC=python3"
        )
    ) else (
        set "PYEXEC=python"
    )
) else (
    set "PYEXEC=py -3"
)

REM Create venv if not present
if not exist "%VENV_DIR%\" (
    echo Creating venv...
    %PYEXEC% -m venv "%VENV_DIR%"
    if %errorlevel% neq 0 (
        echo Failed to create venv.
        pause
        exit /b 1
    )
)

REM Use venv Python to upgrade pip and install dependencies
"%VENV_DIR%\Scripts\python.exe" -m pip install --upgrade pip
"%VENV_DIR%\Scripts\python.exe" -m pip install -r "%REQUIREMENTS%"
if %errorlevel% neq 0 (
    echo Failed to install dependencies.
    pause
    exit /b 1
)

echo Installation completed.
pause
endlocal
exit /b 0
