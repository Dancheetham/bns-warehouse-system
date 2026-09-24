@echo off
REM Builds BNSPrintAgent.exe - a standalone Windows executable of the Print
REM Agent with a system tray icon (right-click for Settings / Exit) and no
REM console window. Run this ON THE WAREHOUSE PC (or any Windows PC) - it
REM can't be cross-built from Linux/Mac.
REM
REM One-time setup before the first build:
REM     pip install -r requirements.txt
REM
REM Then, any time you want to (re)build the .exe:
REM     build_exe.bat
REM
REM The finished .exe appears in dist\BNSPrintAgent.exe - copy that one file
REM to wherever you want it to live (e.g. its own folder, or straight into
REM the Windows Startup folder - see README.md) and double-click it. No
REM Python installation is needed on the PC that runs the .exe, only on the
REM PC that builds it.

pyinstaller --onefile --windowed --noconfirm ^
    --name "BNSPrintAgent" ^
    --icon "assets\icon.ico" ^
    --add-data "assets;assets" ^
    agent.py

echo.
echo Done - see dist\BNSPrintAgent.exe
pause
