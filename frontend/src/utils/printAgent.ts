export interface PrintResult {
  printed: boolean;
}

/**
 * Tries the local print agent first (genuinely silent, no dialog, no new
 * tab) - if it's not reachable (not installed/running on this PC), falls
 * back to opening the PDF in a new tab so printing is never a dead end.
 * Shared between picking notes and shipping labels - same mechanism, just a
 * different configured printer name for each.
 */
export async function printPdf(pdfBlob: Blob, agentUrl: string, printerName: string): Promise<PrintResult> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 1500);
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
