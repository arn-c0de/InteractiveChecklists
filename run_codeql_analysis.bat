@echo off
REM CodeQL Multi-Language Analysis Script
REM Analyzes both Java/Kotlin (Android) and Python (DCS Scripts)

setlocal enabledelayedexpansion

echo ===============================================
echo CodeQL Multi-Language Analysis
echo ===============================================
echo.

REM Check if CodeQL is installed
where codeql >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: CodeQL CLI not found in PATH
    echo Please install CodeQL CLI from: https://github.com/github/codeql-cli-binaries/releases
    pause
    exit /b 1
)

echo [1/7] Cleaning previous CodeQL databases...
if exist codeql-db-java rmdir /s /q codeql-db-java
if exist codeql-db-python rmdir /s /q codeql-db-python
if exist java-analysis.sarif del /q java-analysis.sarif
if exist python-analysis.sarif del /q python-analysis.sarif
echo Done.
echo.

echo [2/7] Creating Java/Kotlin database (Android)...
echo This may take several minutes...
echo Using Debug build (Release build has compilation errors)...
codeql database create codeql-db-java --language=java-kotlin --command="gradlew.bat clean assembleDebug --no-daemon" --overwrite
if %errorlevel% neq 0 (
    echo ERROR: Failed to create Java/Kotlin database
    pause
    exit /b 1
)
echo Done.
echo.

echo [3/7] Creating Python database (Scripts)...
codeql database create codeql-db-python --language=python --source-root=scripts --overwrite
if %errorlevel% neq 0 (
    echo ERROR: Failed to create Python database
    pause
    exit /b 1
)
echo Done.
echo.

echo [4/6] Downloading CodeQL query packs (first run only)...
codeql pack download codeql/java-queries 2>nul
codeql pack download codeql/python-queries 2>nul
echo Done.
echo.

echo [5/6] Analyzing Java/Kotlin code...
codeql database analyze codeql-db-java codeql/java-queries --format=sarif-latest --output=java-analysis.sarif
if %errorlevel% neq 0 (
    echo WARNING: Java/Kotlin analysis completed with errors
)
echo Done.
echo.

echo [6/6] Analyzing Python code...
codeql database analyze codeql-db-python codeql/python-queries --format=sarif-latest --output=python-analysis.sarif
if %errorlevel% neq 0 (
    echo WARNING: Python analysis completed with errors
)
echo Done.
echo.

echo [7/7] Summary
echo ===============================================
echo Java/Kotlin results: java-analysis.sarif
echo Python results:      python-analysis.sarif
echo.
echo Databases created:
echo   - codeql-db-java
echo   - codeql-db-python
echo ===============================================
echo.

REM Check if SARIF files exist
if exist java-analysis.sarif (
    echo Java/Kotlin analysis: SUCCESS
) else (
    echo Java/Kotlin analysis: FAILED
)

if exist python-analysis.sarif (
    echo Python analysis: SUCCESS
) else (
    echo Python analysis: FAILED
)

echo.
echo Analysis complete. Import SARIF files into your IDE or view with:
echo   codeql database analyze --format=text
echo.
pause
