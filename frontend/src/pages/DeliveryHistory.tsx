import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { downloadFile } from "../components/ReportCard";
import { formatDateTime } from "../utils/format";
import { DeliveryHistoryView } from "../types";
import { dpdTrackingUrl } from "../utils/tracking";

const STATUS_STYLES: Record<string, string> = {
  PARTIALLY_DESPATCHED: "bg-amber-100 text-amber-700",
  INVOICE_PENDING: "bg-slate-100 text-slate-600",
  COMPLETED: "bg-emerald-100 text-emerald-700",
};

export default function DeliveryHistory() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const { data: deliveries, isLoading } = useQuery({
    queryKey: ["delivery-history", from, to],
    queryFn: async () =>
      (
        await api.get<DeliveryHistoryView[]>("/delivery-history", {
          params: { from: from || undefined, to: to || undefined },
        })
      ).data,
  });

  const filtered = useMemo(() => {
    const list = deliveries ?? [];
    const term = search.trim().toLowerCase();
    if (!term) return list;
    return list.filter(
      (d) =>
        d.orderNumber.toLowerCase().includes(term) ||
        (d.companyName ?? "").toLowerCase().includes(term) ||
        (d.deliveryName ?? "").toLowerCase().includes(term) ||
        (d.deliveryPostcode ?? "").toLowerCase().includes(term) ||
        (d.consignmentNumber ?? "").toLowerCase().includes(term)
    );
  }, [deliveries, search]);

  const exportExcel = async () => {
    setExportError(null);
    setExporting(true);
    try {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      await downloadFile(`/delivery-history/export?${params.toString()}`, "delivery-history.xlsx");
    } catch (e) {
      setExportError(e instanceof Error ? e.message : "Couldn't generate the export");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">Delivery History</h2>
          <p className="text-slate-500">Every order that's actually been despatched, most recent first.</p>
        </div>
        <button
          onClick={exportExcel}
          disabled={exporting}
          className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {exporting ? "Generating..." : "Export to Excel"}
        </button>
      </div>
      {exportError && <p className="text-sm text-red-600 mb-4">{exportError}</p>}

      <div className="flex gap-3 mb-4 flex-wrap items-end">
        <div className="flex-1 min-w-[240px]">
          <label className="block text-xs font-medium text-slate-500 mb-1">Search</label>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Order number, company, delivery name, postcode or consignment number..."
            className="w-full border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 outline-none text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Despatched from</label>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="input" />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Despatched to</label>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="input" />
        </div>
        <span className="text-sm text-slate-500 mb-2">{filtered.length} record(s)</span>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">Order #</th>
              <th className="px-3 py-2">Despatched</th>
              <th className="px-3 py-2">Company</th>
              <th className="px-3 py-2">Delivery Name</th>
              <th className="px-3 py-2">Postcode</th>
              <th className="px-3 py-2">Courier</th>
              <th className="px-3 py-2">Delivery Method</th>
              <th className="px-3 py-2">Consignment #</th>
              <th className="px-3 py-2 text-right">Parcels</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={11} className="px-3 py-4 text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={11} className="px-3 py-4 text-slate-400">
                  {search ? `No deliveries match "${search}".` : "No orders have been despatched yet."}
                </td>
              </tr>
            )}
            {filtered.map((d) => (
              <tr key={d.orderId} className="hover:bg-slate-50 cursor-pointer" onClick={() => navigate(`/delivery-history/${d.orderId}`)}>
                <td className="px-3 py-2 font-medium text-slate-800">{d.orderNumber}</td>
                <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(d.despatchedAt)}</td>
                <td className="px-3 py-2">{d.companyName ?? "-"}</td>
                <td className="px-3 py-2">{d.deliveryName ?? "-"}</td>
                <td className="px-3 py-2">{d.deliveryPostcode ?? "-"}</td>
                <td className="px-3 py-2">{d.courier ?? "-"}</td>
                <td className="px-3 py-2">{d.deliveryMethod ?? "-"}</td>
                <td className="px-3 py-2">{d.consignmentNumber ?? "-"}</td>
                <td className="px-3 py-2 text-right">{d.parcelCount}</td>
                <td className="px-3 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded font-medium whitespace-nowrap ${STATUS_STYLES[d.orderStatus] ?? "bg-slate-100 text-slate-600"}`}>
                    {d.orderStatus.replace(/_/g, " ")}
                  </span>
                </td>
                <td className="px-3 py-2 text-right" onClick={(e) => e.stopPropagation()}>
                  {d.consignmentNumber && d.courier === "DPD" && (
                    <a
                      href={dpdTrackingUrl(d.consignmentNumber, d.deliveryPostcode)}
                      target="_blank"
                      rel="noreferrer"
                      className="text-emerald-600 hover:underline text-xs"
                    >
                      Track
                    </a>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
