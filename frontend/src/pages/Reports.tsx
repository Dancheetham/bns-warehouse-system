import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "../api/client";

async function downloadFile(url: string, fallbackName: string) {
  const response = await api.get(url, { responseType: "blob" });
  const disposition = response.headers["content-disposition"] as string | undefined;
  const match = disposition?.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? fallbackName;

  const blobUrl = window.URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}

function ReportCard({
  title,
  description,
  onDownload,
  extra,
}: {
  title: string;
  description: string;
  onDownload: () => void;
  extra?: React.ReactNode;
}) {
  const mutation = useMutation({ mutationFn: async () => onDownload() });

  return (
    <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5">
      <h3 className="font-medium text-slate-800 mb-1">{title}</h3>
      <p className="text-sm text-slate-500 mb-4">{description}</p>
      {extra}
      <button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
      >
        {mutation.isPending ? "Generating..." : "Download Excel"}
      </button>
      {mutation.isError && (
        <p className="text-sm text-red-600 mt-2">{(mutation.error as Error).message}</p>
      )}
    </div>
  );
}

export default function Reports() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Reports</h2>
      <p className="text-slate-500 mb-6">
        Generated live from current data and downloaded as an Excel spreadsheet.
      </p>

      <div className="grid md:grid-cols-2 gap-4">
        <ReportCard
          title="Stock Levels by Location"
          description="Every product broken down by bin, with available, quarantined, allocated, dispatched and returned quantities."
          onDownload={() => downloadFile("/reports/stock-levels", "stock-levels.xlsx")}
        />

        <ReportCard
          title="Full Stock Item Export"
          description="Every individual unit in the warehouse - MAC, serial, WiFi MAC, batch, bin, and status."
          onDownload={() => downloadFile("/reports/stock-items", "stock-items.xlsx")}
        />

        <ReportCard
          title="Stock Movement History"
          description="Every recorded stock movement - receipts, moves, dispatches - with who did it and when. Leave the dates blank for the full history."
          onDownload={() => {
            const params = new URLSearchParams();
            if (from) params.set("from", from);
            if (to) params.set("to", to);
            const query = params.toString();
            return downloadFile(
              `/reports/movements${query ? `?${query}` : ""}`,
              "stock-movements.xlsx"
            );
          }}
          extra={
            <div className="flex gap-2 mb-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1">From</label>
                <input
                  type="date"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="border border-slate-300 rounded px-2 py-1.5 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">To</label>
                <input
                  type="date"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="border border-slate-300 rounded px-2 py-1.5 text-sm"
                />
              </div>
            </div>
          }
        />
      </div>
    </div>
  );
}
