# BNS Print Agent

Makes "Print Picking Note" genuinely silent - no new tab, no print dialog, no
choosing a printer by hand. It sends straight to a named printer.

## Why this exists

A web page (any web page, not just this one) is deliberately **not allowed**
to pick a printer or skip the print dialog - that's a browser security
restriction, not a limitation of this app. The only way around it is a small
trusted program running on the actual PC, which this is. The browser sends
the PDF to it over `localhost`, and this script hands it straight to Windows'
printing system with no dialog.

## One-time setup (on the warehouse PC)

1. **Install Python** if it isn't already (Windows 10/11 usually has it, or
   get it from python.org - any recent 3.x version is fine).

2. **Install [SumatraPDF](https://www.sumatrapdfreader.org/download-free-pdf-reader)**
   (free, portable, no admin rights needed for the portable version). This is
   what actually talks to the printer silently - regular Windows tools don't
   have a reliable no-dialog PDF print option, but SumatraPDF does
   (`-print-to` / `-silent` flags).

   Default expected install path: `C:\Program Files\SumatraPDF\SumatraPDF.exe`
   If you installed it somewhere else, either edit `SUMATRA_PATH` near the top
   of `agent.py`, or set an environment variable before running it:
   ```
   set SUMATRA_PATH=C:\wherever\you\put\it\SumatraPDF.exe
   ```

3. **Run the agent:**
   ```
   python agent.py
   ```
   Leave the window open. You should see:
   ```
   BNS Print Agent listening on http://localhost:9191
   ```

4. **Set the printer name** in the app itself: go to **Settings** in the
   warehouse system and enter the exact Windows printer name (Settings →
   Printers & Scanners on the PC will show you the exact name to copy). Leave
   it blank to just use whatever the PC's default printer is.

5. **Test it**: open any order and click "Print Picking Note" - it should
   print immediately with no dialog. If the agent isn't running, the app
   falls back to opening the PDF in a new tab instead, so nothing is ever a
   dead end.

## Running it automatically at Windows startup (optional)

Easiest approach: create a shortcut to `agent.py` (or a small `.bat` file
containing `python agent.py`) and place it in:
```
shell:startup
```
(paste that into the Windows Run dialog - it opens the Startup folder).

## Troubleshooting

- **"SumatraPDF not found"** - check the path in step 2 is correct.
- **Nothing prints, no error** - check the printer name in Settings exactly
  matches what Windows calls it (case and spacing matter to some print
  drivers).
- **Browser can't reach the agent** - the agent only listens on `localhost`,
  so it must be running on the same PC as the browser tab that's printing.
  If your warehouse team uses a shared terminal, the agent needs to run on
  that terminal specifically.
