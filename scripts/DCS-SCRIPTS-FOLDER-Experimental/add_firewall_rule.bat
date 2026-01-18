@echo off
echo Adding Windows Firewall rules for DCS DataPad Server...
echo.
echo This requires Administrator privileges!
echo.

netsh advfirewall firewall add rule name="DCS DataPad - Handshake Port 5011" dir=in action=allow protocol=UDP localport=5011
netsh advfirewall firewall add rule name="DCS DataPad - Data Port 5010" dir=in action=allow protocol=UDP localport=5010

echo.
echo Done! Firewall rules added.
pause
