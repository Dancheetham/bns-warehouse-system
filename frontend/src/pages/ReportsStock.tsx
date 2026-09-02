import { useState } from "react";
import { downloadFile, ReportCard } from "../components/ReportCard";

export default function ReportsStock() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Stock Reports</h2>
      <p className="text-slate-500 mb-6">
        Generated live from current data and downloaded as an Excel spreadsheet.
      </p>

      <div className="grid md:grid-cols-2 gap-4">
        <ReportCard
          title="Stock Levels by Location"
          description="Every product broken down by bin, with available, quarantined, allocated, despatched and returned quantities."
          onDownload={() => downloadFile("/reports/stock-levels", "stock-levels.xlsx")}
        />

        <ReportCard
          title="Full Stock Item Export"
          description="Every individual unit in the warehouse - MAC, serial, WiFi MAC, batch, bin, and status."
          onDownload={() => downloadFile("/reports/stock-items", "stock-items.xlsx")}
        />

        <ReportCard
          title="Stock Movement History"
          description="Every recorded stock movement - receipts, moves, despatches - with who did it and when. Leave the dates blank for the full history."
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
