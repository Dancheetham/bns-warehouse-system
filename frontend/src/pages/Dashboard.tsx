import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { PurchaseOrder, Product, Order, OrderStatus } from "../types";
import PieChart from "../components/PieChart";

const statusLabels: Record<OrderStatus, string> = {
  ON_HOLD: "On Hold",
  AWAITING_DESPATCH: "Awaiting Despatch",
  CANCELLED: "Cancelled",
  COMPLETED: "Completed",
  PARTIALLY_DESPATCHED: "Partially Despatched",
  AWAITING_CONVERSION: "Awaiting Conversion",
};

const statusColors: Record<OrderStatus, string> = {
  ON_HOLD: "#f59e0b",
  AWAITING_DESPATCH: "#3b82f6",
  CANCELLED: "#94a3b8",
  COMPLETED: "#10b981",
  PARTIALLY_DESPATCHED: "#a855f7",
  AWAITING_CONVERSION: "#f97316",
};

export default function Dashboard() {
  const { data: products } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const { data: purchaseOrders } = useQuery({
    queryKey: ["purchase-orders"],
    queryFn: async () => (await api.get<PurchaseOrder[]>("/purchase-orders")).data,
  });

  const { data: orders } = useQuery({
    queryKey: ["orders"],
    queryFn: async () => (await api.get<Order[]>("/orders")).data,
  });

  const awaitingStock = purchaseOrders?.filter(
    (po) => po.status === "AWAITING_STOCK" || po.status === "PART_RECEIVED"
  );

  const statusCounts = (Object.keys(statusLabels) as OrderStatus[]).map((status) => ({
    status,
    count: orders?.filter((o) => o.status === status).length ?? 0,
  }));

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-6">Dashboard</h2>

      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Products</p>
          <p className="text-3xl font-semibold text-slate-800">{products?.length ?? "-"}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Purchase Orders</p>
          <p className="text-3xl font-semibold text-slate-800">{purchaseOrders?.length ?? "-"}</p>
        </div>
        <div className="bg-white rounded-lg shadow-sm p-5 border border-slate-200">
          <p className="text-sm text-slate-500">Awaiting / Part Received</p>
          <p className="text-3xl font-semibold text-amber-600">{awaitingStock?.length ?? "-"}</p>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 mb-8">
        <div className="px-5 py-3 border-b border-slate-200">
          <h3 className="font-medium text-slate-700">Sales orders by status</h3>
        </div>
        <div className="p-5 flex flex-col sm:flex-row items-center gap-6">
          <PieChart
            data={statusCounts.map((s) => ({ label: statusLabels[s.status], value: s.count, color: statusColors[s.status] }))}
          />
          <div className="flex-1 w-full grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2">
            {statusCounts.map((s) => (
              <div key={s.status} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span className="w-2.5 h-2.5 rounded-full inline-block" style={{ backgroundColor: statusColors[s.status] }} />
                  {statusLabels[s.status]}
                </span>
                <span className="font-medium text-slate-800">{s.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200">
        <div className="px-5 py-3 border-b border-slate-200">
          <h3 className="font-medium text-slate-700">Purchase orders awaiting goods in</h3>
        </div>
        <div className="divide-y divide-slate-100">
          {awaitingStock?.length ? (
            awaitingStock.map((po) => (
              <Link
                to={`/purchase-orders/${po.id}`}
                key={po.id}
                className="flex justify-between items-center px-5 py-3 hover:bg-slate-50"
              >
                <div>
                  <p className="font-medium text-slate-800">{po.poNumber}</p>
                  <p className="text-sm text-slate-500">{po.supplier.name}</p>
                </div>
                <span className="text-xs font-medium px-2 py-1 rounded bg-amber-100 text-amber-700">
                  {po.status.replace("_", " ")}
                </span>
              </Link>
            ))
          ) : (
            <p className="px-5 py-6 text-sm text-slate-400">Nothing waiting on stock right now.</p>
          )}
        </div>
      </div>
    </div>
  );
}
