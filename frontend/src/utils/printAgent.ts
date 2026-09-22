export interface PrintResult {
  printed: boolean;
}

/**
 * Tries the local print agent first (genuinely silent, no dialog, no new
 * tab) - if it's not reachable (not installed/running on this PC), falls
 * back to opening the PDF in a new tab so printing is never a dead end.
 * Shared between picking notes and shipping labels - same mechanism, just a
 * different configured printer name for each.
 *
 * The timeout here is deliberately generous (8s), not the ~1.5s it used to
 * be - the agent's own print call can legitimately take a few seconds
 * (launching the PDF viewer, the OS spooler picking up the job), so a short
 * timeout was liable to misreport a real, working print as "agent not
 * reachable" and fall back to a browser tab even when nothing was actually
 * wrong. An agent that's genuinely not running still fails fast (an
 * immediate connection refused), so this doesn't meaningfully slow down
 * that case at all.
 */
export async function printPdf(pdfBlob: Blob, agentUrl: string, printerName: string): Promise<PrintResult> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  const agentResponse = await fetch(agentUrl, {
    method: "POST",
    headers: { "X-Printer-Name": printerName, "Content-Type": "application/pdf" },
    body: pdfBlob,
    signal: controller.signal,
  }).catch(() => null);
  clearTimeout(timeout);

  if (agentResponse && agentResponse.ok) {
    return { printed: true };
  }
  const blobUrl = window.URL.createObjectURL(pdfBlob);
  window.open(blobUrl, "_blank");
  return { printed: false };
}

/**
 * Sends raw printer command data (ZPL, DPD's own thermal label format)
 * straight to the configured label printer via the local print agent, with
 * no browser or PDF rendering step at all. This is what makes the label
 * come out at its real physical size - a browser or PDF viewer has no idea
 * what size label is loaded in the printer and scales to a full page
 * instead, which is what was producing labels that didn't fit the label
 * stock. Unlike printPdf, there's no sensible browser-tab fallback for raw
 * ZPL (it's not something a browser can render or print), so a failure here
 * just reports "not printed" - the caller should tell the operator to check
 * the print agent is running rather than opening anything.
 */
export async function printRaw(rawData: string, agentUrl: string, printerName: string): Promise<PrintResult> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  const agentResponse = await fetch(agentUrl, {
    method: "POST",
    headers: { "X-Printer-Name": printerName, "X-Print-Format": "raw", "Content-Type": "text/plain" },
    body: rawData,
    signal: controller.signal,
  }).catch(() => null);
  clearTimeout(timeout);
  return { printed: !!(agentResponse && agentResponse.ok) };
}

/**
 * DPD's shipping labels come back as raw HTML (printerType 0), not a PDF, so
 * they can't go through printPdf/the print agent above (that only ever
 * posts PDFs). Opens the label in a new tab and triggers the browser's own
 * print dialog as soon as it's loaded, so the operator lands straight on
 * "pick a printer and print" instead of having to remember to hit Ctrl+P
 * themselves. Returns false if the tab was blocked by a pop-up blocker (the
 * blob was still created and nothing is printed), so the caller can tell
 * the operator to allow pop-ups rather than silently doing nothing.
 *
 * This still shows the browser's print dialog rather than printing
 * silently in the background - genuinely silent printing of a DPD label
 * would mean requesting a raw thermal format (EPL/CLP/ZPL) instead of HTML
 * and sending that straight to a configured label printer, which needs a
 * printer profile set up for that format first.
 */
export function openAndPrintHtmlLabel(htmlBlob: Blob): boolean {
  const blobUrl = window.URL.createObjectURL(htmlBlob);
  const labelWindow = window.open(blobUrl, "_blank");
  if (!labelWindow) {
    return false;
  }
  labelWindow.onload = () => {
    labelWindow.focus();
    labelWindow.print();
  };
  return true;
}
