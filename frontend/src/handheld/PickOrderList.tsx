import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { OrderPickSummary } from "../types";

const statusLabel: Record<string, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
};

const statusColor: Record<string, string> = {
  NOT_STARTED: "bg-slate-200 text-slate-600",
  IN_PROGRESS: "bg-amber-100 text-amber-700",
};

export default function PickOrderList() {
  const navigate = useNavigate();

  const { data: orders, isLoading, refetch, isFetching } = useQuery({
    queryKey: ["picking-ready"],
    queryFn: async () => (await api.get<OrderPickSummary[]>("/picking/ready")).data,
    refetchInterval: 15000,
  });

  return (
    <div className="min-h-screen bg-slate-950 text-white flex flex-col">
      <header className="px-4 py-4 flex items-center justify-between border-b border-slate-800">
        <div>
          <button onClick={() => navigate("/handheld")} className="text-xs text-slate-500 mb-1 block">
            ← Handheld Home
          </button>
          <h1 className="text-lg font-semibold">Orders to Pick</h1>
          <p className="text-xs text-slate-400">Tap an order to start</p>
        </div>
        <button
          onClick={() => refetch()}
          className="text-sm bg-slate-800 px-3 py-2 rounded-lg active:bg-slate-700"
        >
          {isFetching ? "..." : "Refresh"}
        </button>
      </header>

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {isLoading && <p className="text-slate-400 text-center py-8">Loading...</p>}
        {orders && orders.length === 0 && (
          <p className="text-slate-400 text-center py-8">Nothing waiting to be picked.</p>
        )}
        {orders?.map((o) => (
          <button
            key={o.orderId}
            onClick={() => navigate(`/handheld/pick/${o.orderId}`)}
            className="w-full text-left bg-slate-900 rounded-xl p-4 active:bg-slate-800 border border-slate-800"
          >
            <div className="flex items-center justify-between">
              <span className="text-xl font-bold">{o.orderNumber}</span>
              <span className={`text-xs px-2 py-1 rounded-full ${statusColor[o.pickingStatus] ?? ""}`}>
                {statusLabel[o.pickingStatus] ?? o.pickingStatus}
              </span>
            </div>
            <p className="text-slate-300 mt-1">{o.customerName}</p>
            <p className="text-slate-500 text-sm mt-1">
              {o.lineCount} line{o.lineCount === 1 ? "" : "s"}
              {o.pickedBy ? ` · ${o.pickedBy}` : ""}
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}
