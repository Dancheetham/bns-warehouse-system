import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { OrderPickSummary } from "../types";

const statusLabel: Record<string, string> = {
  COMPLETE: "Fully picked",
  PARTIAL: "Short picked",
};

const statusColor: Record<string, string> = {
  COMPLETE: "bg-emerald-100 text-emerald-700",
  PARTIAL: "bg-amber-100 text-amber-700",
};

export default function Despatch() {
  const navigate = useNavigate();

  const { data: orders, isLoading } = useQuery({
    queryKey: ["ready-to-pack"],
    queryFn: async () => (await api.get<OrderPickSummary[]>("/despatch/ready-to-pack")).data,
    refetchInterval: 15000,
  });

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Despatch</h2>
      <p className="text-slate-500 mb-6">
        Orders that have finished picking on the handheld. Pack each into cartons, then confirm despatch to consume
        the stock and print labels.
      </p>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-left">
            <tr>
              <th className="px-4 py-2">Order</th>
              <th className="px-4 py-2">Customer</th>
              <th className="px-4 py-2">Lines</th>
              <th className="px-4 py-2">Picked By</th>
              <th className="px-4 py-2">Pick Status</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {orders?.length === 0 && !isLoading && (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                  Nothing ready to pack yet.
                </td>
              </tr>
            )}
            {orders?.map((o) => (
              <tr key={o.orderId} className="border-t border-slate-100">
                <td className="px-4 py-2 font-medium">{o.orderNumber}</td>
                <td className="px-4 py-2">{o.customerName}</td>
                <td className="px-4 py-2">{o.lineCount}</td>
                <td className="px-4 py-2">{o.pickedBy ?? "—"}</td>
                <td className="px-4 py-2">
                  <span className={`text-xs px-2 py-1 rounded-full ${statusColor[o.pickingStatus] ?? ""}`}>
                    {statusLabel[o.pickingStatus] ?? o.pickingStatus}
                  </span>
                </td>
                <td className="px-4 py-2 text-right">
                  <button
                    onClick={() => navigate(`/despatch/${o.orderId}`)}
                    className="bg-emerald-600 text-white text-xs px-3 py-1.5 rounded-md hover:bg-emerald-500"
                  >
                    Pack & Ship
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
