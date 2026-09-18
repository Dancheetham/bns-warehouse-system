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
