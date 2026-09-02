import { useState } from "react";
import { useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { PurchaseOrder, ImportResult } from "../types";

export default function PurchaseOrderDetail() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: po, isLoading } = useQuery({
    queryKey: ["purchase-order", id],
    queryFn: async () => (await api.get<PurchaseOrder>(`/purchase-orders/${id}`)).data,
  });

  const importMutation = useMutation({
    mutationFn: async () => {
      const formData = new FormData();
      formData.append("file", file as File);
      return (
        await api.post<ImportResult>(`/purchase-orders/${id}/import`, formData, {
          headers: { "Content-Type": "multipart/form-data" },
        })
      ).data;
    },
    onSuccess: (data) => {
      setResult(data);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["purchase-order", id] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setResult(null);
    },
  });

  if (isLoading || !po) return <p className="text-slate-400">Loading...</p>;

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-2xl font-semibold text-slate-800">{po.poNumber}</h2>
        <p className="text-slate-500">{po.supplier.name}</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden mb-6">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">SKU</th>
              <th className="px-4 py-2">Product</th>
              <th className="px-4 py-2">Qty Ordered</th>
              <th className="px-4 py-2">Qty Received</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {po.lines.map((line) => (
              <tr key={line.id}>
                <td className="px-4 py-2 font-medium">{line.product.sku}</td>
                <td className="px-4 py-2">{line.product.name}</td>
                <td className="px-4 py-2">{line.quantityOrdered}</td>
                <td className="px-4 py-2">{line.quantityReceived}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {po.status === "DRAFT" || po.status === "AWAITING_STOCK" ? (
        <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5">
          <h3 className="font-medium text-slate-700 mb-3">Import supplier shipment spreadsheet</h3>
          <p className="text-sm text-slate-500 mb-3">
            Expects columns: <code className="bg-slate-100 px-1 rounded">SKU</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">MAC</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">SERIAL</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">BATCH</code>,{" "}
            <code className="bg-slate-100 px-1 rounded">WIFI_MAC</code> (optional). The total per SKU
            must match this PO exactly, or the whole import is rejected.
          </p>
          <div className="flex gap-3 items-center">
            <input
              type="file"
              accept=".xlsx,.xls,.csv"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-sm"
            />
            <button
              disabled={!file || importMutation.isPending}
              onClick={() => importMutation.mutate()}
              className="bg-slate-800 text-white text-sm px-4 py-2 rounded-md hover:bg-slate-700 disabled:opacity-50"
            >
              {importMutation.isPending ? "Importing..." : "Import"}
            </button>
          </div>

          {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

          {result && (
            <div className="mt-4">
              {result.success ? (
                <p className="text-sm text-emerald-700 mb-2">
                  ✓ Imported {result.cartonsCreated} cartons / {result.itemsCreated} items. This PO
                  is now ready for Goods In.
                </p>
              ) : (
                <p className="text-sm text-red-600 mb-2">✗ Import rejected - nothing was saved.</p>
              )}
              <table className="w-full text-sm border border-slate-200 rounded overflow-hidden">
                <thead className="bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th className="px-3 py-1.5">SKU</th>
                    <th className="px-3 py-1.5">PO Qty</th>
                    <th className="px-3 py-1.5">Spreadsheet Qty</th>
                    <th className="px-3 py-1.5">Match</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {result.lineValidation.map((row) => (
                    <tr key={row.sku}>
                      <td className="px-3 py-1.5">{row.sku}</td>
                      <td className="px-3 py-1.5">{row.poQuantity}</td>
                      <td className="px-3 py-1.5">{row.spreadsheetQuantity}</td>
                      <td className="px-3 py-1.5">{row.matches ? "✓" : "✗"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {result.errors.length > 0 && (
                <ul className="mt-2 text-sm text-red-600 list-disc list-inside">
                  {result.errors.map((e, i) => (
                    <li key={i}>{e}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      ) : (
        <p className="text-sm text-slate-500">
          Status is <strong>{po.status}</strong> - go to the Goods In screen to continue booking this
          order in.
        </p>
      )}
    </div>
  );
}
