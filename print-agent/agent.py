"""
BNS Warehouse Print Agent
=========================

Runs on the warehouse PC (or any PC with the target printer installed) and
listens on localhost for print jobs sent from the browser. This is what makes
genuinely silent, specific-printer printing possible - something a web page
can never do on its own, because browsers deliberately don't allow JavaScript
to pick a printer or skip the print dialog. This agent is a small trusted
program running locally that CAN do that, using the OS's own printing tools.

Requires SumatraPDF (free, portable, no install needed) for the actual
silent PDF printing on Windows - see README.md in this folder for setup.

Runs two ways:

- **System tray mode** (default, if `pystray` + `Pillow` are installed, or
  when built into the standalone .exe via build_exe.bat): shows a printer
  icon in the system tray. Right-click it for Settings (set the SumatraPDF
  path from a GUI, no more editing this file or environment variables) and
  Exit. This is the intended way to run it day-to-day - no console window,
  nothing to remember to reopen.
- **Console mode** (fallback): if the tray dependencies aren't installed,
  it just runs the print server in the foreground with `python agent.py`,
  exactly as before. Still fully functional - only the tray icon and
  Settings GUI are unavailable.

Usage:
    python agent.py
    (leave it running in the background, or set it up to run at Windows
    startup - see README.md. Once built as an .exe via build_exe.bat, it
    can be put in the Windows Startup folder directly and needs no console
    window at all.)
"""

import http.server
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import uuid

PORT = 9191

# When run as plain "python agent.py", AGENT_DIR is this file's folder as
# normal. When run as the PyInstaller-built .exe (see build_exe.bat),
# bundled data files (assets/icon.ico) are unpacked to a temp folder at
# startup and exposed via sys._MEIPASS instead - PyInstaller's own
# documented pattern for finding them at runtime.
if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
    AGENT_DIR = sys._MEIPASS
else:
    AGENT_DIR = os.path.dirname(os.path.abspath(__file__))
ICON_PATH = os.path.join(AGENT_DIR, "assets", "icon.ico")

# "Open agent folder" (tray menu) should still open the real installed
# location, not the temp extraction folder above, when running as the .exe.
OPEN_FOLDER_DIR = os.path.dirname(os.path.abspath(sys.argv[0]))

# --------------------------------------------------------------------------
# Settings: SumatraPDF path (and anything else added to Settings later)
# persists in a small JSON config file so it survives restarts and can be
# changed from the tray Settings window without touching code or env vars.
# --------------------------------------------------------------------------

CONFIG_DIR = os.path.join(
    os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), ".config"),
    "BNSPrintAgent",
)
CONFIG_PATH = os.path.join(CONFIG_DIR, "config.json")

DEFAULT_SUMATRA_PATH = r"C:\Program Files\SumatraPDF\SumatraPDF.exe"


def _load_config() -> dict:
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                return json.load(f)
        except (OSError, json.JSONDecodeError):
            pass
    return {}


def _save_config(cfg: dict) -> None:
    os.makedirs(CONFIG_DIR, exist_ok=True)
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)


_config = _load_config()

# Resolution order for the Sumatra path: saved Settings GUI value first (the
# whole point of the GUI is that it should win), then the SUMATRA_PATH
# environment variable (kept for anyone who set it up the old way), then the
# plain default install path.
_sumatra_path = (
    _config.get("sumatra_path")
    or os.environ.get("SUMATRA_PATH")
    or DEFAULT_SUMATRA_PATH
)


def get_sumatra_path() -> str:
    return _sumatra_path


def set_sumatra_path(path: str) -> None:
    global _sumatra_path, _config
    _sumatra_path = path
    _config["sumatra_path"] = path
    _save_config(_config)


# --------------------------------------------------------------------------
# HTTP server - unchanged behaviour from before, just reads the path via
# get_sumatra_path() now instead of a fixed module constant, so a change
# made in the Settings window takes effect on the very next print with no
# restart needed.
# --------------------------------------------------------------------------


class PrintHandler(http.server.BaseHTTPRequestHandler):

    def _cors_headers(self):
        # The browser is on a different origin (http://<warehouse-pc-ip>:8081)
        # talking to this agent on localhost - needs CORS headers to be allowed.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Printer-Name, X-Print-Format")

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors_headers()
        self.end_headers()

    def do_GET(self):
        # Simple health check the frontend can use to see if the agent is running
        # before it decides whether to fall back to a normal browser print.
        if self.path == "/health":
            self.send_response(200)
            self._cors_headers()
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        else:
            self.send_response(404)
            self._cors_headers()
            self.end_headers()

    def do_POST(self):
        if self.path != "/print":
            self.send_response(404)
            self._cors_headers()
            self.end_headers()
            return

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)
        printer_name = self.headers.get("X-Printer-Name", "").strip()
        # "raw" = send the bytes straight to the printer untouched (used for
        # DPD's ZPL shipping labels, so they print at the label's real
        # physical size). Anything else (or missing) = the original PDF
        # path via SumatraPDF, used for picking notes and the placeholder
        # sample labels.
        print_format = self.headers.get("X-Print-Format", "pdf").strip().lower()

        try:
            if print_format == "raw":
                self._print_raw(body, printer_name)
            else:
                self._print_pdf(body, printer_name)
            self.send_response(200)
            self._cors_headers()
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "printed"}).encode())
        except Exception as e:
            self.send_response(500)
            self._cors_headers()
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())

    def _print_pdf(self, pdf_bytes: bytes, printer_name: str):
        sumatra_path = get_sumatra_path()
        if not os.path.exists(sumatra_path):
            raise RuntimeError(
                f"SumatraPDF not found at {sumatra_path}. Set the correct path from the "
                "tray icon's Settings window (right-click the printer icon), or install it "
                "- see README.md."
            )

        # A genuinely unique name, not just a millisecond timestamp - now that
        # the server handles requests concurrently (ThreadingHTTPServer), two
        # print jobs landing in the same millisecond is no longer a
        # vanishingly-unlikely edge case worth ignoring.
        tmp_path = os.path.join(tempfile.gettempdir(), f"bns-print-{uuid.uuid4().hex}.pdf")
        with open(tmp_path, "wb") as f:
            f.write(pdf_bytes)

        try:
            if printer_name:
                cmd = [sumatra_path, "-print-to", printer_name, "-silent", tmp_path]
            else:
                cmd = [sumatra_path, "-print-to-default", "-silent", tmp_path]

            result = subprocess.run(cmd, capture_output=True, timeout=30)
            if result.returncode != 0:
                raise RuntimeError(f"SumatraPDF exited with code {result.returncode}: {result.stderr.decode(errors='ignore')}")
        finally:
            # Give the print spooler a moment to pick up the file before deleting it
            time.sleep(2)
            try:
                os.remove(tmp_path)
            except OSError:
                pass

    def _print_raw(self, data: bytes, printer_name: str):
        # Raw printer command data (ZPL, from DPD's thermal label format) -
        # sent straight to the printer's own engine with the Windows RAW
        # datatype, which skips GDI/driver reprocessing entirely. That's the
        # whole point: a label sent this way always comes out at the label
        # stock's real physical size, because nothing tries to scale it to
        # a page the way printing HTML or a PDF through a normal driver
        # does. Needs pywin32 (`pip install pywin32`) - the PDF path above
        # doesn't need it, so it's only imported here, on demand.
        try:
            import win32print
        except ImportError:
            raise RuntimeError(
                "Raw label printing needs the pywin32 package - install it on this "
                "PC with 'pip install pywin32', then restart the agent."
            )

        printer = printer_name or win32print.GetDefaultPrinter()
        handle = win32print.OpenPrinter(printer)
        try:
            job_id = win32print.StartDocPrinter(handle, 1, ("BNS Warehouse Label", None, "RAW"))
            try:
                win32print.StartPagePrinter(handle)
                win32print.WritePrinter(handle, data)
                win32print.EndPagePrinter(handle)
            finally:
                win32print.EndDocPrinter(handle)
        finally:
            win32print.ClosePrinter(handle)

    def log_message(self, format, *args):
        # Quieter than the default, which logs every request to stderr
        print(f"[print-agent] {self.address_string()} - {format % args}")


def run_server():
    # ThreadingHTTPServer, not plain HTTPServer - the handler deliberately
    # blocks for a couple of seconds per print (subprocess call + a sleep
    # to let the spooler pick up the file before deleting it), and a plain
    # HTTPServer would queue every other request behind that single blocking
    # call rather than handling them concurrently. Matters more now that the
    # app can trigger more than one print in quick succession (e.g. picking
    # note on release, label on despatch).
    server = http.server.ThreadingHTTPServer(("localhost", PORT), PrintHandler)
    print(f"BNS Print Agent listening on http://localhost:{PORT}")
    server.serve_forever()


# --------------------------------------------------------------------------
# Settings GUI (Tkinter - part of the standard library, no extra dependency)
# --------------------------------------------------------------------------


def show_settings_window():
    import tkinter as tk
    from tkinter import filedialog, messagebox

    root = tk.Tk()
    root.title("BNS Print Agent - Settings")
    root.resizable(False, False)
    try:
        root.iconbitmap(ICON_PATH)
    except Exception:
        pass  # icon is cosmetic only - a missing/unsupported .ico shouldn't block the window

    padding = {"padx": 12, "pady": 8}

    tk.Label(root, text="BNS Print Agent", font=("Segoe UI", 12, "bold")).grid(
        row=0, column=0, columnspan=3, sticky="w", **padding
    )
    tk.Label(root, text=f"Listening on http://localhost:{PORT}", fg="#555").grid(
        row=1, column=0, columnspan=3, sticky="w", padx=12
    )

    tk.Label(root, text="SumatraPDF.exe location:").grid(row=2, column=0, sticky="w", **padding)
    path_var = tk.StringVar(value=get_sumatra_path())
    path_entry = tk.Entry(root, textvariable=path_var, width=52)
    path_entry.grid(row=3, column=0, columnspan=3, sticky="we", padx=12)

    status_var = tk.StringVar(value="")
    status_label = tk.Label(root, textvariable=status_var, fg="#b00")
    status_label.grid(row=4, column=0, columnspan=3, sticky="w", padx=12)

    def refresh_status():
        if os.path.exists(path_var.get().strip()):
            status_var.set("Found \u2713")
            status_label.config(fg="#0a0")
        else:
            status_var.set("Not found at this path")
            status_label.config(fg="#b00")

    def browse():
        chosen = filedialog.askopenfilename(
            title="Select SumatraPDF.exe",
            filetypes=[("SumatraPDF executable", "SumatraPDF.exe"), ("Executable files", "*.exe"), ("All files", "*.*")],
            initialdir=os.path.dirname(path_var.get()) if os.path.dirname(path_var.get()) else "C:\\",
        )
        if chosen:
            path_var.set(chosen)
            refresh_status()

    def save():
        new_path = path_var.get().strip()
        if not new_path:
            messagebox.showerror("BNS Print Agent", "Enter or browse to a path for SumatraPDF.exe first.")
            return
        set_sumatra_path(new_path)
        messagebox.showinfo("BNS Print Agent", "Saved. Takes effect on the next print - no restart needed.")
        root.destroy()

    btn_frame = tk.Frame(root)
    btn_frame.grid(row=5, column=0, columnspan=3, sticky="e", padx=12, pady=(4, 12))
    tk.Button(btn_frame, text="Browse...", command=browse).pack(side="left", padx=(0, 8))
    tk.Button(btn_frame, text="Save", command=save, default="active").pack(side="left", padx=(0, 8))
    tk.Button(btn_frame, text="Cancel", command=root.destroy).pack(side="left")

    refresh_status()
    root.mainloop()


# --------------------------------------------------------------------------
# System tray icon
# --------------------------------------------------------------------------


def run_tray():
    import pystray
    from PIL import Image

    def on_settings(icon, menu_item):
        # Tkinter needs its own thread here since the tray icon's run loop
        # already owns the main thread on Windows.
        threading.Thread(target=show_settings_window, daemon=True).start()

    def on_open_folder(icon, menu_item):
        try:
            os.startfile(OPEN_FOLDER_DIR)  # Windows-only, which is the only platform this ships on
        except AttributeError:
            pass

    def on_exit(icon, menu_item):
        icon.stop()
        os._exit(0)  # the HTTP server thread is a daemon thread, so a hard exit is fine and instant

    image = Image.open(ICON_PATH)
    menu = pystray.Menu(
        pystray.MenuItem(f"BNS Print Agent - listening on :{PORT}", None, enabled=False),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Settings...", on_settings, default=True),
        pystray.MenuItem("Open agent folder", on_open_folder),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit", on_exit),
    )
    tray_icon = pystray.Icon("bns-print-agent", image, "BNS Print Agent", menu)
    tray_icon.run()  # blocks - must run on the main thread


if __name__ == "__main__":
    server_thread = threading.Thread(target=run_server, daemon=True)
    server_thread.start()

    try:
        run_tray()
    except ImportError:
        # pystray/Pillow aren't installed - fall back to plain console mode,
        # exactly how this agent worked before the tray icon existed. Still
        # fully functional for printing; only Settings-from-a-GUI and the
        # tray icon itself are unavailable (edit SUMATRA_PATH's env var, or
        # `pip install pystray pillow` and re-run, to get them back).
        print("(tray icon unavailable - install with 'pip install pystray pillow' for it; "
              "running in console mode for now)")
        print("Leave this window open. Press Ctrl+C to stop.")
        try:
            server_thread.join()
        except KeyboardInterrupt:
            print("\nStopping.")
