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

Usage:
    python agent.py
    (leave the window open/running in the background, or set it up to run
    at Windows startup - see README.md)
"""

import http.server
import json
import os
import subprocess
import tempfile
import time

PORT = 9191

# Path to SumatraPDF.exe - update this if you installed it somewhere else.
SUMATRA_PATH = os.environ.get(
    "SUMATRA_PATH",
    r"C:\Program Files\SumatraPDF\SumatraPDF.exe",
)


class PrintHandler(http.server.BaseHTTPRequestHandler):

    def _cors_headers(self):
        # The browser is on a different origin (http://<warehouse-pc-ip>:8081)
        # talking to this agent on localhost - needs CORS headers to be allowed.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-Printer-Name")

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
        pdf_bytes = self.rfile.read(content_length)
        printer_name = self.headers.get("X-Printer-Name", "").strip()

        try:
            self._print_pdf(pdf_bytes, printer_name)
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
        if not os.path.exists(SUMATRA_PATH):
            raise RuntimeError(
                f"SumatraPDF not found at {SUMATRA_PATH}. Install it or set the "
                "SUMATRA_PATH environment variable - see README.md."
            )

        tmp_path = os.path.join(tempfile.gettempdir(), f"bns-picking-note-{int(time.time() * 1000)}.pdf")
        with open(tmp_path, "wb") as f:
            f.write(pdf_bytes)

        try:
            if printer_name:
                cmd = [SUMATRA_PATH, "-print-to", printer_name, "-silent", tmp_path]
            else:
                cmd = [SUMATRA_PATH, "-print-to-default", "-silent", tmp_path]

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

    def log_message(self, format, *args):
        # Quieter than the default, which logs every request to stderr
        print(f"[print-agent] {self.address_string()} - {format % args}")


if __name__ == "__main__":
    server = http.server.HTTPServer(("localhost", PORT), PrintHandler)
    print(f"BNS Print Agent listening on http://localhost:{PORT}")
    print("Leave this window open. Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping.")
