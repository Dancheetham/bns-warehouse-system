import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { PurchaseOrder, Product, Supplier } from "../types";
import { formatDate } from "../utils/format";

interface LineDraft {
  productId: string;
  quantityOrdered: string;
}

const STATUS_COLORS: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-600",
  AWAITING_STOCK: "bg-amber-100 text-amber-700",
  PART_RECEIVED: "bg-blue-100 text-blue-700",
  RECEIVED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function PurchaseOrders() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [supplierId, setSupplierId] = useState("");
  const [expectedDate, setExpectedDate] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([{ productId: "", quantityOrdered: "" }]);
  const [error, setError] = useState<string | null>(null);

  const { data: purchaseOrders, isLoading } = useQuery({
    queryKey: ["purchase-orders"],
    queryFn: async () => (await api.get<PurchaseOrder[]>("/purchase-orders")).data,
  });

  const { data: suppliers } = useQuery({
    queryKey: ["suppliers"],
    queryFn: async () => (await api.get<Supplier[]>("/suppliers")).data,
  });

  const { data: products } = useQuery({
    queryKey: ["products"],
    queryFn: async () => (await api.get<Product[]>("/products")).data,
  });

  const createMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post("/purchase-orders", {
          supplierId: Number(supplierId),
          expectedDate: expectedDate || null,
          lines: lines
            .filter((l) => l.productId && l.quantityOrdered)
            .map((l) => ({
              productId: Number(l.productId),
              quantityOrdered: Number(l.quantityOrdered),
            })),
        })
      ).data,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
      setShowForm(false);
      setSupplierId("");
      setExpectedDate("");
      setLines([{ productId: "", quantityOrdered: "" }]);
      setError(null);
    },
    onError: (err: Error) => setError(err.message),
  });

  const updateLine = (index: number, field: keyof LineDraft, value: string) => {
    setLines((prev) => prev.map((l, i) => (i === index ? { ...l, [field]: value } : l)));
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">Purchase Orders</h2>
        <button
          onClick={() => setShowForm((v) => !v)}
          className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700"
        >
          {showForm ? "Cancel" : "New Purchase Order"}
        </button>
      </div>

      {showForm && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            createMutation.mutate();
          }}
          className="bg-white border border-slate-200 rounded-lg p-5 mb-6 space-y-4"
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Supplier</label>
              <select
                required
                value={supplierId}
                onChange={(e) => setSupplierId(e.target.value)}
                className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              >
                <option value="">Select a supplier...</option>
                {suppliers?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-500 mb-1">Expected Date</label>
              <input
                type="date"
                value={expectedDate}
                onChange={(e) => setExpectedDate(e.target.value)}
                className="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-500 mb-2">Lines</label>
            <div className="space-y-2">
              {lines.map((line, i) => (
                <div key={i} className="flex gap-2">
                  <select
                    value={line.productId}
                    onChange={(e) => updateLine(i, "productId", e.target.value)}
                    className="flex-1 border border-slate-300 rounded px-3 py-2 text-sm"
                  >
                    <option value="">Select product...</option>
                    {products?.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.sku} - {p.name}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    min={1}
                    placeholder="Qty"
                    value={line.quantityOrdered}
                    onChange={(e) => updateLine(i, "quantityOrdered", e.target.value)}
                    className="w-24 border border-slate-300 rounded px-3 py-2 text-sm"
                  />
                </div>
              ))}
            </div>
            <button
              type="button"
              onClick={() => setLines((prev) => [...prev, { productId: "", quantityOrdered: "" }])}
              className="mt-2 text-sm text-slate-600 hover:text-slate-900"
            >
              + Add line
            </button>
          </div>

          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
          >
            {createMutation.isPending ? "Saving..." : "Create Purchase Order"}
          </button>
          {error && <p className="text-sm text-red-600">{error}</p>}
        </form>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">PO Number</th>
              <th className="px-4 py-2">Supplier</th>
              <th className="px-4 py-2">Expected</th>
              <th className="px-4 py-2">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-4 text-slate-400" colSpan={4}>
                  Loading...
                </td>
              </tr>
            )}
            {purchaseOrders?.map((po) => (
              <tr key={po.id} className="hover:bg-slate-50">
                <td className="px-4 py-2 font-medium">
                  <Link to={`/purchase-orders/${po.id}`} className="text-blue-600 hover:underline">
                    {po.poNumber}
                  </Link>
                </td>
                <td className="px-4 py-2">{po.supplier.name}</td>
                <td className="px-4 py-2">{po.expectedDate ? formatDate(po.expectedDate) : "-"}</td>
                <td className="px-4 py-2">
                  <span className={`text-xs px-2 py-1 rounded ${STATUS_COLORS[po.status]}`}>
                    {po.status.replace("_", " ")}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
