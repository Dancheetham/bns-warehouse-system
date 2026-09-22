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

3. **Install [pywin32](https://pypi.org/project/pywin32/)** if you'll be
   printing DPD shipping labels (not needed for picking notes alone):
   ```
   pip install pywin32
   ```
   This is what lets the agent hand ZPL bytes straight to the printer with
   no GDI/driver scaling in between - the actual fix for labels coming out
   the wrong size.

4. **Run the agent:**
   ```
   python agent.py
   ```
   Leave the window open. You should see:
   ```
   BNS Print Agent listening on http://localhost:9191
   ```

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

## Running it automatically at Windows startup (optional)

Easiest approach: create a shortcut to `agent.py` (or a small `.bat` file
containing `python agent.py`) and place it in:
```
shell:startup
```
(paste that into the Windows Run dialog - it opens the Startup folder).

## Troubleshooting

- **"SumatraPDF not found"** - check the path in step 2 is correct.
- **"Raw label printing needs the pywin32 package"** - run
  `pip install pywin32` on the PC running the agent, then restart it.
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
