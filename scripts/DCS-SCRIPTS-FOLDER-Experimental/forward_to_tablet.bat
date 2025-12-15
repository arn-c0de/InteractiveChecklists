@echo off
rem forward_to_tablet.bat - Forward DCS parsed JSONL to an Android tablet via UDP
rem Usage: forward_to_tablet.bat [HOST] [PORT]
rem Example: forward_to_tablet.bat 192.168.1.42 5010


rem Prevent double window: only start new window if not already in child
if "%FORWARDER_CHILD%"=="1" goto :child

setlocal
set SCRIPT_DIR=%~dp0
rem Remove trailing backslash if present (except for root like C:\)
if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%
set HOST=%1
if "%HOST%"=="" set HOST=192.168.178.69
set PORT=%2
if "%PORT%"=="" set PORT=5010

echo Starting forwarding to %HOST%:%PORT% in a new window (keeps running)...
set FORWARDER_CHILD=1
rem Use nested quotes so paths with spaces are passed correctly to the child cmd
start "DCS Forward" cmd /k ""%~f0" "%HOST%" "%PORT%""
goto :eof

:child
rem Now in the child window, run the loop
 :loop
 echo Starting forward_parsed_udp.py to %HOST%:%PORT% ...
 cd /d "%SCRIPT_DIR%"
 python forward_parsed_udp.py --file player_aircraft_parsed.jsonl --host "%HOST%" --port "%PORT%" --verbose
 echo Script exited. Restarting in 5 seconds... (Press Ctrl+C to quit)
 timeout /t 5 >nul
 goto loop
endlocal