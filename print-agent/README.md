# BNS Print Agent

Makes printing genuinely silent - no new tab, no print dialog, no choosing a
printer by hand. It sends straight to a named printer. Handles two kinds of
job:

- **Picking notes and placeholder sample labels** - PDFs, printed via
  SumatraPDF.
- **DPD shipping labels** - raw ZPL (Zebra's label command language), sent
  directly to the printer so it comes out at the label's real physical
  size, rather than being scaled to a page the way a PDF or HTML label
  would be. Needs a Zebra (or ZPL-compatible) label printer and the
  pywin32 package - see step 3 below.

It runs as a small icon in the Windows system tray - right-click it for
**Settings** (set the SumatraPDF location from a window, no file editing)
and **Exit**. No console window, nothing to remember to reopen.

## Why this exists

A web page (any web page, not just this one) is deliberately **not allowed**
to pick a printer or skip the print dialog - that's a browser security
restriction, not a limitation of this app. The only way around it is a small
trusted program running on the actual PC, which this is. The browser sends
the PDF to it over `localhost`, and this script hands it straight to Windows'
printing system with no dialog.

## Two ways to run it

**Option A - the standalone .exe (recommended for the warehouse PC).** A
single `BNSPrintAgent.exe` with the tray icon and Settings window built in -
nothing to install on the PC that runs it, not even Python. Someone with
Python needs to *build* it once (see "Building the .exe" below); after that,
the .exe itself is just copied wherever it's needed.

**Option B - running the Python script directly** (`python agent.py`) - good
for testing changes, or if you'd rather not build an .exe at all. Works
exactly the same way, tray icon included, as long as its two GUI packages are
installed (`pip install pystray pillow` - see step 1 below); without them it
still runs, just as a plain console window with no tray icon or Settings GUI,
the same as it always did.

## One-time setup (on the warehouse PC)

1. **Install Python** if it isn't already (Windows 10/11 usually has it, or
   get it from python.org - any recent 3.x version is fine), then install
   this folder's dependencies:
   ```
   pip install -r requirements.txt
   ```
   This pulls in `pystray` + `Pillow` (the tray icon and its Settings
   window), `pywin32` (raw ZPL label printing - see step 3), and
   `pyinstaller` (only needed if you're building the .exe, Option A above).

2. **Install [SumatraPDF](https://www.sumatrapdfreader.org/download-free-pdf-reader)**
   (free, portable, no admin rights needed for the portable version). This is
   what actually talks to the printer silently - regular Windows tools don't
   have a reliable no-dialog PDF print option, but SumatraPDF does
   (`-print-to` / `-silent` flags).

   Default expected install path: `C:\Program Files\SumatraPDF\SumatraPDF.exe`.
   If you installed it somewhere else, set the correct path from the tray
   icon: right-click it → **Settings** → **Browse...** to the real
   `SumatraPDF.exe` → **Save**. Takes effect on the very next print, no
   restart needed. (The old `SUMATRA_PATH` environment variable still works
   too, but the Settings window is the easier way now and takes priority
   over it - see "Settings, and where they're stored" below.)

3. **Install [pywin32](https://pypi.org/project/pywin32/)** if you'll be
   printing DPD shipping labels (not needed for picking notes alone) -
   already covered by `pip install -r requirements.txt` in step 1 on
   Windows, or on its own:
   ```
   pip install pywin32
   ```
   This is what lets the agent hand ZPL bytes straight to the printer with
   no GDI/driver scaling in between - the actual fix for labels coming out
   the wrong size.

4. **Run the agent** - either the built .exe (double-click `BNSPrintAgent.exe`,
   see "Building the .exe" below) or the script:
   ```
   python agent.py
   ```
   A printer icon appears in the system tray (you may need to click the
   `^` arrow in the taskbar to see it, then optionally drag it out so it's
   always visible). Right-click it any time for **Settings** or **Exit**.

5. **Set the printer name** in the app itself: go to **Settings** in the
   warehouse system, under DPD, and enter the exact Windows printer name for
   "Shipping label printer" (Settings → Printers & Scanners on the PC will
   show you the exact name to copy - for a USB-connected Zebra, this is
   whatever name Windows gave it when the driver was installed). Leave it
   blank to just use whatever the PC's default printer is. The same field
   also sets the printer used for DPD shipping labels.

6. **Test it**: open any order and click "Print Picking Note" - it should
   print immediately with no dialog. Then book a DPD shipment and print its
   label - that one goes out as raw ZPL, so it should come out sized exactly
   to the label, not scaled to a page. If the agent isn't running, picking
   notes fall back to opening the PDF in a new tab instead, so that's never a
   dead end - but a ZPL label needs the agent running, since a browser can't
   render or print raw ZPL data itself.

## Building the .exe

On a Windows PC with Python and this folder's dependencies installed
(`pip install -r requirements.txt`, which includes PyInstaller):

```
build_exe.bat
```

This bundles `agent.py` and its tray icon (`assets/icon.ico`) into a single
`dist\BNSPrintAgent.exe` - no Python installation needed on whatever PC
ultimately runs it. `--windowed` means no console window ever appears, only
the tray icon. Copy that one .exe file to wherever it should live (its own
folder is fine) and, optionally, into the Windows Startup folder so it
starts automatically (see below) - it needs `assets/icon.ico` at *build*
time only, not alongside the finished .exe.

Re-run `build_exe.bat` any time `agent.py` changes, to pick up the update.

## Settings, and where they're stored

The SumatraPDF path set from the tray's **Settings** window is saved to a
small JSON file at `%APPDATA%\BNSPrintAgent\config.json` and is read again
automatically every time the agent starts, so it only needs setting once per
PC. It takes priority over the `SUMATRA_PATH` environment variable if both
are set; if neither is set, it falls back to the default install path
(`C:\Program Files\SumatraPDF\SumatraPDF.exe`).

## Running it automatically at Windows startup (optional)

Easiest approach: place a shortcut to `BNSPrintAgent.exe` (or, if running the
script directly instead, a shortcut to `agent.py` / a small `.bat` file
containing `python agent.py`) into:
```
shell:startup
```
(paste that into the Windows Run dialog - it opens the Startup folder).

## Troubleshooting

- **"SumatraPDF not found"** - open Settings from the tray icon and check the
  path, or use **Browse...** to find it properly.
- **"Raw label printing needs the pywin32 package"** - run
  `pip install pywin32` on the PC running the agent, then restart it (only
  relevant when running `agent.py` directly - it's bundled into the .exe).
- **No tray icon appears, agent runs in a console window instead** -
  `pystray`/`Pillow` aren't installed; run `pip install pystray pillow` and
  restart `agent.py` (not applicable to the built .exe, which always has
  them bundled in).
- **Nothing prints, no error** - check the printer name in Settings exactly
  matches what Windows calls it (case and spacing matter to some print
  drivers).
- **DPD labels print but at the wrong size, or as garbled text/barcodes** -
  this means the printer isn't a ZPL-compatible label printer, or its
  Windows driver is intercepting and reprocessing the raw data instead of
  passing it straight through. Check the printer's driver has a "raw"/pass-
  through mode if one is offered, and confirm the DPI setting under Settings
  → DPD matches the printer (203 vs 300 dpi) - a mismatch there can also
  throw off barcode scaling.
- **Browser can't reach the agent** - the agent only listens on `localhost`,
  so it must be running on the same PC as the browser tab that's printing.
  If your warehouse team uses a shared terminal, the agent needs to run on
  that terminal specifically.
