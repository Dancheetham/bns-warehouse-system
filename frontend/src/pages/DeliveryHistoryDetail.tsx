import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { downloadFile } from "../components/ReportCard";
import { formatDateTime } from "../utils/format";
import { dpdTrackingUrl } from "../utils/tracking";
import { useToast } from "../components/ToastContext";
import { DeliveryHistoryDetailView } from "../types";

// A plain inline SVG rather than an icon-font/library dependency - matches
// how small one-off icons are already done elsewhere in this app.
function ClipboardIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="8" y="2" width="8" height="4" rx="1" />
      <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
    </svg>
  );
}

export default function DeliveryHistoryDetail() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [cartonSummaryOpen, setCartonSummaryOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["delivery-history", orderId],
    queryFn: async () => (await api.get<DeliveryHistoryDetailView>(`/delivery-history/${orderId}`)).data,
  });

  const exportExcel = async () => {
    setExportError(null);
    setExporting(true);
    try {
      await downloadFile(`/delivery-history/${orderId}/export`, `delivery-history-order-${orderId}.xlsx`);
    } catch (e) {
      setExportError(e instanceof Error ? e.message : "Couldn't generate the export");
    } finally {
      setExporting(false);
    }
  };

  const items = data?.items ?? [];

  const filteredItems = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return items;
    return items.filter(
      (item) =>
        item.sku.toLowerCase().includes(term) ||
        item.productName.toLowerCase().includes(term) ||
        (item.macAddress ?? "").toLowerCase().includes(term) ||
        (item.serialNumber ?? "").toLowerCase().includes(term) ||
        (item.batchCode ?? "").toLowerCase().includes(term)
    );
  }, [items, search]);

  // Grouped for the Carton Summary dropdown - one section per carton
  // number, in carton order (the backend already sorts cartonSummary by
  // carton then SKU, so Map insertion order here follows that).
  const cartonGroups = useMemo(() => {
    const groups = new Map<number, { cartonNumber: number; skus: { sku: string; productName: string; quantity: number }[] }>();
    for (const row of data?.cartonSummary ?? []) {
      if (!groups.has(row.cartonNumber)) {
        groups.set(row.cartonNumber, { cartonNumber: row.cartonNumber, skus: [] });
      }
      groups.get(row.cartonNumber)!.skus.push({ sku: row.sku, productName: row.productName, quantity: row.quantity });
    }
    return [...groups.values()];
  }, [data]);

  const copyColumn = async (label: string, values: (string | undefined)[]) => {
    const cleaned = values.filter((v): v is string => !!v && v.trim() !== "");
    if (cleaned.length === 0) {
      showToast(`No ${label.toLowerCase()} to copy.`);
      return;
    }
    try {
      await navigator.clipboard.writeText(cleaned.join("\n"));
      showToast(`Copied ${cleaned.length} ${label.toLowerCase()}${cleaned.length === 1 ? "" : "s"} to the clipboard.`);
    } catch {
      showToast("Couldn't copy to the clipboard - your browser may be blocking clipboard access.");
    }
  };

  if (isLoading || !data) {
    return <p className="text-slate-500">Loading...</p>;
  }

  const { order } = data;

  return (
    <div>
      <button onClick={() => navigate("/delivery-history")} className="text-sm text-slate-500 hover:text-slate-800 mb-3">
        ← Back to Delivery History
      </button>

      <div className="flex justify-between items-start mb-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-800">
            {order.orderNumber} <span className="text-slate-400 font-normal">· {order.companyName ?? order.deliveryName ?? ""}</span>
          </h2>
          <p className="text-slate-500">Despatched {formatDateTime(order.despatchedAt)}</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate(`/sales-activity/${order.orderId}`)}
            className="bg-slate-100 text-slate-700 text-sm px-4 py-2 rounded-md hover:bg-slate-200"
          >
            Open Order
          </button>
          <button
            onClick={exportExcel}
            disabled={exporting}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {exporting ? "Generating..." : "Export to Excel"}
          </button>
        </div>
      </div>
      {exportError && <p className="text-sm text-red-600 mb-4">{exportError}</p>}

      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
        <div>
          <p className="text-slate-400 text-xs mb-1">Delivery Name</p>
          <p className="text-slate-700">{order.deliveryName ?? "-"}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Postcode</p>
          <p className="text-slate-700">{order.deliveryPostcode ?? "-"}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Courier</p>
          <p className="text-slate-700">{order.courier ?? "-"}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Delivery Method</p>
          <p className="text-slate-700">{order.deliveryMethod ?? "-"}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Consignment Number</p>
          <p className="text-slate-700">{order.consignmentNumber ?? "-"}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Parcels</p>
          <p className="text-slate-700">{order.parcelCount}</p>
        </div>
        <div>
          <p className="text-slate-400 text-xs mb-1">Status</p>
          <p className="text-slate-700">{order.orderStatus.replace(/_/g, " ")}</p>
        </div>
        {order.consignmentNumber && order.courier === "DPD" && (
          <div className="flex items-end">
            <a
              href={dpdTrackingUrl(order.consignmentNumber, order.deliveryPostcode)}
              target="_blank"
              rel="noreferrer"
              className="bg-slate-800 text-white text-xs px-3 py-1.5 rounded hover:bg-slate-700"
            >
              Track Shipment →
            </a>
          </div>
        )}
      </div>

      {data.previousShipments.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg mb-4 overflow-hidden">
          <div className="px-4 py-2 border-b border-slate-200 text-sm font-medium text-slate-600">
            Previous Shipments{" "}
            <span className="text-slate-400 font-normal">
              · this order was reopened for {data.previousShipments.length === 1 ? "an extra shipment" : `${data.previousShipments.length} extra shipments`}
              , shown above as the current one
            </span>
          </div>
          <table className="w-full text-sm">
            <thead className="text-left text-slate-500">
              <tr>
                <th className="px-4 py-1.5">Shipped</th>
                <th className="px-4 py-1.5">Courier</th>
                <th className="px-4 py-1.5">Delivery Method</th>
                <th className="px-4 py-1.5">Consignment #</th>
                <th className="px-4 py-1.5 text-right">Shipping Cost</th>
                <th className="px-4 py-1.5"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.previousShipments.map((s, i) => (
                <tr key={i}>
                  <td className="px-4 py-1.5 whitespace-nowrap">{s.shippedAt ? formatDateTime(s.shippedAt) : "-"}</td>
                  <td className="px-4 py-1.5">{s.courier ?? "-"}</td>
                  <td className="px-4 py-1.5">{s.courierMethod ?? "-"}</td>
                  <td className="px-4 py-1.5">{s.dpdConsignmentNumber ?? "-"}</td>
                  <td className="px-4 py-1.5 text-right">{s.shippingCost != null ? `£${Number(s.shippingCost).toFixed(2)}` : "-"}</td>
                  <td className="px-4 py-1.5 text-right">
                    {s.dpdConsignmentNumber && s.courier === "DPD" && (
                      <a
                        href={dpdTrackingUrl(s.dpdConsignmentNumber, order.deliveryPostcode)}
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
      )}

      {cartonGroups.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg mb-4 overflow-hidden">
          <button
            onClick={() => setCartonSummaryOpen((v) => !v)}
            className="w-full flex justify-between items-center px-4 py-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            <span>
              Carton Summary <span className="text-slate-400 font-normal">· {cartonGroups.length} carton(s)</span>
            </span>
            <span className="text-slate-400 text-xs">{cartonSummaryOpen ? "▲ Hide" : "▼ Show"}</span>
          </button>
          {cartonSummaryOpen && (
            <div className="border-t border-slate-200 divide-y divide-slate-100">
              {cartonGroups.map((group) => (
                <div key={group.cartonNumber} className="px-4 py-3">
                  <p className="text-xs font-semibold text-slate-500 mb-1.5">Carton {group.cartonNumber}</p>
                  <table className="w-full text-sm">
                    <tbody className="divide-y divide-slate-50">
                      {group.skus.map((s) => (
                        <tr key={s.sku}>
                          <td className="py-1 pr-3 font-medium text-slate-800 w-28">{s.sku}</td>
                          <td className="py-1 pr-3 text-slate-600">{s.productName}</td>
                          <td className="py-1 text-right text-slate-700 w-16">×{s.quantity}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="mb-4">
        <label className="block text-xs font-medium text-slate-500 mb-1">Search</label>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="SKU, product, MAC address, serial number or batch code..."
          className="w-full border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 outline-none text-sm"
        />
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">SKU</th>
              <th className="px-3 py-2">Product</th>
              <th className="px-3 py-2">
                <span className="inline-flex items-center gap-1.5">
                  MAC Address
                  <button
                    title="Copy all MAC addresses shown below, one per line"
                    onClick={() => copyColumn("MAC address", filteredItems.map((i) => i.macAddress))}
                    className="text-slate-400 hover:text-emerald-600"
                  >
                    <ClipboardIcon />
                  </button>
                </span>
              </th>
              <th className="px-3 py-2">
                <span className="inline-flex items-center gap-1.5">
                  Serial Number
                  <button
                    title="Copy all serial numbers shown below, one per line"
                    onClick={() => copyColumn("serial number", filteredItems.map((i) => i.serialNumber))}
                    className="text-slate-400 hover:text-emerald-600"
                  >
                    <ClipboardIcon />
                  </button>
                </span>
              </th>
              <th className="px-3 py-2">Batch Code</th>
              <th className="px-3 py-2 text-right">Qty</th>
              <th className="px-3 py-2">Carton</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filteredItems.length === 0 && (
              <tr>
                <td colSpan={7} className="px-3 py-4 text-slate-400">
                  {search ? `No items match "${search}".` : "Nothing recorded as shipped for this order yet."}
                </td>
              </tr>
            )}
            {filteredItems.map((item, i) => (
              <tr key={i} className="hover:bg-slate-50">
                <td className="px-3 py-2 font-medium text-slate-800">{item.sku}</td>
                <td className="px-3 py-2">{item.productName}</td>
                <td className="px-3 py-2">{item.macAddress ?? "-"}</td>
                <td className="px-3 py-2">{item.serialNumber ?? "-"}</td>
                <td className="px-3 py-2">{item.batchCode ?? "-"}</td>
                <td className="px-3 py-2 text-right">{item.quantity}</td>
                <td className="px-3 py-2">
                  {item.cartonNumber != null ? `Carton ${item.cartonNumber}` : "Split across cartons"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
