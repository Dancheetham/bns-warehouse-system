import { useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { Location, MoveItemsResult, StockItemSummary } from "../types";
import { useAuth } from "../auth/AuthContext";
import BinSelect from "../components/BinSelect";

export default function StockMovement() {
  const { user } = useAuth();
  const [scanValue, setScanValue] = useState("");
  const [pending, setPending] = useState<StockItemSummary[]>([]);
  const [scanError, setScanError] = useState<string | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);
  const [toLocationId, setToLocationId] = useState("");
  const [result, setResult] = useState<MoveItemsResult | null>(null);
  const scanInputRef = useRef<HTMLInputElement>(null);

  const { data: locations } = useQuery({
    queryKey: ["locations"],
    queryFn: async () => (await api.get<Location[]>("/locations")).data,
  });

  const addToPending = (items: StockItemSummary[]) => {
    setPending((prev) => {
      const existingIds = new Set(prev.map((i) => i.id));
      const newOnes = items.filter((i) => !existingIds.has(i.id));
      return [...prev, ...newOnes];
    });
  };

  const scanMutation = useMutation({
    mutationFn: async (value: string) => {
      const looksLikeMac = /^[0-9a-fA-F]{2}([:\-]?[0-9a-fA-F]{2}){5}$/.test(value.trim());
      if (looksLikeMac) {
        return [(await api.get<StockItemSummary>(`/stock-items/mac/${encodeURIComponent(value)}`)).data];
      }
      // Try serial first; if not found, try as a batch/carton code (which may return many items)
      try {
        return [(await api.get<StockItemSummary>(`/stock-items/serial/${encodeURIComponent(value)}`)).data];
      } catch {
        return (await api.get<StockItemSummary[]>(`/stock-items/batch/${encodeURIComponent(value)}`)).data;
      }
    },
    onSuccess: (items) => {
      addToPending(items);
      setScanError(null);
      setScanValue("");
      scanInputRef.current?.focus();
    },
    onError: (err: Error) => {
      setScanError(err.message);
      setScanValue("");
      scanInputRef.current?.focus();
    },
  });

  const moveMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<MoveItemsResult>("/stock-items/move", {
          stockItemIds: pending.map((i) => i.id),
          toLocationId: Number(toLocationId),
          movedBy: user?.name ?? "warehouse",
        })
      ).data,
    onSuccess: (data) => {
      setResult(data);
      setMoveError(null);
      setPending([]);
      setToLocationId("");
    },
    onError: (err: Error) => {
      setMoveError(err.message);
      setResult(null);
    },
  });

  const handleScanSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!scanValue.trim()) return;
    scanMutation.mutate(scanValue.trim());
  };

  const removeItem = (id: number) => {
    setPending((prev) => prev.filter((i) => i.id !== id));
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold text-slate-800 mb-2">Stock Movement</h2>
      <p className="text-slate-500 mb-6">
        Scan or type a MAC address, serial number, or batch/carton code to add it to the move
        list. A batch code adds every device in that carton at once.
      </p>

      {moveError && (
        <div className="mb-6 text-sm rounded px-4 py-3 border bg-red-50 border-red-200 text-red-700">
          {moveError}
        </div>
      )}

      {result && (
        <div
          className={`mb-6 text-sm rounded px-4 py-3 border ${
            result.skippedCount > 0
              ? "bg-amber-50 border-amber-200 text-amber-800"
              : "bg-emerald-50 border-emerald-200 text-emerald-700"
          }`}
        >
          <p>
            Moved {result.movedCount} item{result.movedCount === 1 ? "" : "s"}
            {result.skippedCount > 0 ? `, skipped ${result.skippedCount}` : ""}.
          </p>
          {result.skippedReasons.length > 0 && (
            <ul className="list-disc pl-5 mt-1">
              {result.skippedReasons.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <form onSubmit={handleScanSubmit} className="mb-2">
        <input
          ref={scanInputRef}
          autoFocus
          value={scanValue}
          onChange={(e) => setScanValue(e.target.value)}
          placeholder="Scan or type MAC / serial / batch code..."
          className="w-full text-lg border-2 border-slate-300 focus:border-emerald-500 rounded-lg px-4 py-4 outline-none"
        />
      </form>
      {scanError && <p className="text-sm text-red-600 mb-4">{scanError}</p>}
      {!scanError && <div className="mb-4" />}

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 overflow-hidden mb-6">
        <div className="px-4 py-2 border-b border-slate-200 text-sm font-medium text-slate-600 flex justify-between">
          <span>Ready to move</span>
          <span>{pending.length} item(s)</span>
        </div>
        <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
          {pending.length === 0 && (
            <p className="px-4 py-6 text-sm text-slate-400">Nothing scanned yet.</p>
          )}
          {pending.map((item) => (
            <div key={item.id} className="flex justify-between items-center px-4 py-2 text-sm">
              <div>
                <p className="font-medium text-slate-800 font-mono">
                  {item.macAddress ?? item.serialNumber ?? `Item ${item.id}`}
                </p>
                <p className="text-slate-500">
                  {item.productSku} - {item.productName}
                  {item.batchCode ? ` · Batch ${item.batchCode}` : ""}
                  {item.locationCode ? ` · Currently at ${item.locationCode}` : ""}
                </p>
              </div>
              <button
                onClick={() => removeItem(item.id)}
                className="text-slate-400 hover:text-red-600 text-xs px-2"
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-slate-200 p-5 flex flex-wrap items-end gap-3">
        <div>
          <label className="block text-xs font-medium text-slate-500 mb-1">Move to</label>
          <BinSelect bins={locations ?? []} value={toLocationId} onChange={setToLocationId} className="min-w-[220px]" />
        </div>
        <button
          onClick={() => moveMutation.mutate()}
          disabled={pending.length === 0 || !toLocationId || moveMutation.isPending}
          className="bg-emerald-600 text-white text-sm px-4 py-2 rounded-md hover:bg-emerald-500 disabled:opacity-50"
        >
          {moveMutation.isPending ? "Saving..." : `Move ${pending.length || ""} item(s)`}
        </button>
      </div>
    </div>
  );
}
