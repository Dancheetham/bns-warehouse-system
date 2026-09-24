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

REM Uses "python -m PyInstaller" rather than the bare "pyinstaller" command.
REM pip installs PyInstaller.exe into Python's Scripts folder, which isn't
REM always on PATH even when "python" itself is - "pyinstaller: not
REM recognized" almost always means that, not a failed install. Calling it
REM as a module through "python -m" sidesteps PATH entirely.

where python >nul 2>nul
if errorlevel 1 (
    echo.
    echo ERROR: "python" isn't on PATH. Install Python from python.org first
    echo ^(tick "Add python.exe to PATH" during install^), then re-run this.
    pause
    exit /b 1
)

python -m PyInstaller --version >nul 2>nul
if errorlevel 1 (
    echo.
    echo PyInstaller isn't installed yet - installing this folder's
    echo dependencies first...
    python -m pip install -r requirements.txt
    if errorlevel 1 (
        echo.
        echo ERROR: "pip install -r requirements.txt" failed - see the output
        echo above. Fix that, then re-run build_exe.bat.
        pause
        exit /b 1
    )
)

python -m PyInstaller --onefile --windowed --noconfirm ^
    --name "BNSPrintAgent" ^
    --icon "assets\icon.ico" ^
    --add-data "assets;assets" ^
    agent.py

if errorlevel 1 (
    echo.
    echo ERROR: PyInstaller failed - see the output above for the actual
    echo error. dist\BNSPrintAgent.exe was NOT built.
    pause
    exit /b 1
)

echo.
echo Done - see dist\BNSPrintAgent.exe
pause
