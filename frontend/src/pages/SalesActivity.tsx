import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { Order, OrderType } from "../types";
import { formatDate } from "../utils/format";
import { resolveStatusColors, statusLabel } from "../utils/statusColors";

const TYPE_STYLES: Record<OrderType, string> = {
  ORDER: "bg-slate-100 text-slate-600",
  PAUSED: "bg-amber-100 text-amber-700",
  QUOTE: "bg-purple-100 text-purple-700",
  CREDIT_REFUND: "bg-red-100 text-red-700",
  SCHEDULED: "bg-blue-100 text-blue-700",
};

const SEARCHABLE_FIELDS: (keyof Order)[] = [
  "orderNumber",
  "customerName",
  "orderReference",
  "ecommerceOrderNumber",
  "orderedBy",
  "deliveryName",
  "deliveryTown",
  "deliveryCountry",
  "deliveryPostcode",
];

export default function SalesActivity() {
  const navigate = useNavigate();
  const [selected, setSelected] = useState<Order | null>(null);
  const [search, setSearch] = useState("");

  const { data: orders, isLoading } = useQuery({
    queryKey: ["orders"],
    queryFn: async () => (await api.get<Order[]>("/orders")).data,
  });

  const { data: myUserSettings } = useQuery({
    queryKey: ["my-user-settings"],
    queryFn: async () => (await api.get<Record<string, string>>("/users/me/settings")).data,
  });

  const statusColors = useMemo(() => resolveStatusColors(myUserSettings), [myUserSettings]);

  const filtered = useMemo(() => {
    const list = orders ?? [];
    const term = search.trim().toLowerCase();
    const matches = term
      ? list.filter(
          (order) =>
            SEARCHABLE_FIELDS.some((field) => {
              const value = order[field];
              return typeof value === "string" && value.toLowerCase().includes(term);
            }) || (order.company?.name ?? "").toLowerCase().includes(term)
        )
      : list;
    return [...matches].sort((a, b) => new Date(b.orderDate).getTime() - new Date(a.orderDate).getTime());
  }, [orders, search]);

  return (
    <div className="flex flex-col h-[calc(100vh-140px)]">
      <div className="flex justify-between items-center mb-4 shrink-0">
        <h2 className="text-2xl font-semibold text-slate-800">Sales Activity</h2>
        <button
          onClick={() => navigate("/sales-activity/new")}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          New Order
        </button>
      </div>

      <input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search by order number, customer, reference, delivery details..."
        className="w-full border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-2.5 mb-4 outline-none text-sm shrink-0"
      />

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto flex-[3] mb-4 w-full">
        <table className="w-full text-sm table-fixed">
          <colgroup>
            <col className="w-16" />
            <col className="w-28" />
            <col className="w-24" />
            <col />
            <col className="w-28" />
            <col className="w-32" />
            <col className="w-28" />
            <col />
            <col className="w-28" />
            <col className="w-32" />
            <col className="w-24" />
            <col className="w-36" />
            <col className="w-32" />
          </colgroup>
          <thead className="text-left text-slate-500 border-b border-slate-200 sticky top-0 bg-white">
            <tr>
              <th className="px-2 py-2">Order ID</th>
              <th className="px-2 py-2">Order Number</th>
              <th className="px-2 py-2">Order Date</th>
              <th className="px-2 py-2">Company</th>
              <th className="px-2 py-2">PO Number</th>
              <th className="px-2 py-2">Ecommerce #</th>
              <th className="px-2 py-2">Ordered By</th>
              <th className="px-2 py-2">Delivery Name</th>
              <th className="px-2 py-2">Delivery Town</th>
              <th className="px-2 py-2">Postcode</th>
              <th className="px-2 py-2">Country Code</th>
              <th className="px-2 py-2">Status</th>
              <th className="px-2 py-2">Order Type</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={13} className="px-2 py-4 text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr>
                <td colSpan={13} className="px-2 py-4 text-slate-400">
                  No orders match "{search}".
                </td>
              </tr>
            )}
            {filtered.map((order) => (
              <tr
                key={order.id}
                onClick={() => setSelected(order)}
                onDoubleClick={() => navigate(`/sales-activity/${order.id}`)}
                style={{ backgroundColor: statusColors[order.status] }}
                className={`cursor-pointer ${
                  selected?.id === order.id ? "outline outline-2 outline-emerald-500 -outline-offset-2" : ""
                }`}
              >
                <td className="px-2 py-2 text-slate-700 truncate">{order.id}</td>
                <td className="px-2 py-2 font-medium text-slate-800 truncate">{order.orderNumber}</td>
                <td className="px-2 py-2 whitespace-nowrap">{formatDate(order.orderDate)}</td>
                <td className="px-2 py-2 truncate">{order.company?.name ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.orderReference ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.ecommerceOrderNumber ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.orderedBy ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.deliveryName ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.deliveryTown ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.deliveryPostcode ?? "-"}</td>
                <td className="px-2 py-2 truncate">{order.deliveryCountryCode ?? "-"}</td>
                <td className="px-2 py-2 font-medium whitespace-nowrap">{statusLabel(order.status)}</td>
                <td className="px-2 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded font-medium whitespace-nowrap ${TYPE_STYLES[order.orderType]}`}>
                    {order.orderType.replace(/_/g, " ")}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-auto flex-1 shrink-0">
        <div className="px-4 py-2 border-b border-slate-200 text-sm font-medium text-slate-600 sticky top-0 bg-white">
          {selected ? `Order lines - ${selected.orderNumber}` : "Select an order to see its lines"}
        </div>
        {selected && (
          <table className="w-full text-sm">
            <thead className="text-left text-slate-500">
              <tr>
                <th className="px-4 py-1.5">SKU</th>
                <th className="px-4 py-1.5">Product</th>
                <th className="px-4 py-1.5">Qty Ordered</th>
                <th className="px-4 py-1.5">Qty Despatched</th>
                <th className="px-4 py-1.5">Unit Price</th>
                <th className="px-4 py-1.5">Notes</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {selected.lines.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-3 text-slate-400">
                    No lines on this order.
                  </td>
                </tr>
              )}
              {selected.lines.map((line) => (
                <tr key={line.id}>
                  <td className="px-4 py-1.5 font-medium">{line.product.sku}</td>
                  <td className="px-4 py-1.5">{line.product.name}</td>
                  <td className="px-4 py-1.5">{line.quantityOrdered}</td>
                  <td className="px-4 py-1.5">{line.quantityDespatched}</td>
                  <td className="px-4 py-1.5">{line.unitPrice != null ? `£${Number(line.unitPrice).toFixed(2)}` : "-"}</td>
                  <td className="px-4 py-1.5">{line.notes ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
