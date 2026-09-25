import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { downloadFile } from "../components/ReportCard";
import { formatDateTime } from "../utils/format";
import { dpdTrackingUrl } from "../utils/tracking";
import { DeliveryHistoryDetailView } from "../types";

export default function DeliveryHistoryDetail() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

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

  if (isLoading || !data) {
    return <p className="text-slate-500">Loading...</p>;
  }

  const { order, items } = data;

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

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-3 py-2">SKU</th>
              <th className="px-3 py-2">Product</th>
              <th className="px-3 py-2">MAC Address</th>
              <th className="px-3 py-2">Serial Number</th>
              <th className="px-3 py-2">Batch Code</th>
              <th className="px-3 py-2 text-right">Qty</th>
              <th className="px-3 py-2">Carton</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {items.length === 0 && (
              <tr>
                <td colSpan={7} className="px-3 py-4 text-slate-400">
                  Nothing recorded as shipped for this order yet.
                </td>
              </tr>
            )}
            {items.map((item, i) => (
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
